package com.eternalrealm.ranking;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * All rankings of one season: a {@link Leaderboard} per player category, the GUILD leaderboard and the
 * {@link TimeAttackBoard}. The single owner of ranking state (all changes go through this class).
 *
 * <p><b>Players:</b> {@link #updatePlayer} stores the player's {@link SeasonStats} and recomputes every player
 * category with {@link RankingCalculator}. A category score of 0 means "not ranked" (nobody is listed with zero boss
 * kills). Because scores derive from the stats, {@link #setRules} and {@link #rebuild} recompute them exactly.</p>
 *
 * <p><b>Freezing:</b> when the season ends the caller {@link #freeze()}s the board. Updates and submissions are then
 * ignored (rejected with {@link TimeAttackValidator#SEASON_CLOSED} for time attacks) and the stored scores become final:
 * neither a rules change nor a restart recomputes them. Operator removals stay possible for corrections before the
 * archive is written.</p>
 *
 * <p><b>Persistence:</b> {@link #snapshot()} is immutable and goes to the I/O thread; {@link #revision()} /
 * {@link #isDirty()} / {@link #markSaved(long)} work like those of the character. Not thread-safe: server thread only.</p>
 */
public final class RankingBoard {
    /** Entries per category kept in a {@link SeasonArchive} unless the caller chooses otherwise. */
    public static final int DEFAULT_ARCHIVE_SIZE = 100;
    public static final int MAX_ARCHIVE_SIZE = 10_000;

    private record Standing(String name, SeasonStats stats, long updatedAtMillis) {
    }

    private final SeasonId seasonId;
    private RankingRules rules;
    private final Map<RankingCategory, Leaderboard<UUID>> playerBoards = new EnumMap<>(RankingCategory.class);
    private final Map<UUID, Standing> standings = new HashMap<>();
    private final Leaderboard<String> guilds = new Leaderboard<>();
    private final TimeAttackBoard timeAttack = new TimeAttackBoard();
    private boolean frozen;
    private long revision;
    private long savedRevision;

    /** An empty, open board (a new season). */
    public RankingBoard(SeasonId seasonId, RankingRules rules) {
        this.seasonId = Objects.requireNonNull(seasonId, "seasonId");
        this.rules = Objects.requireNonNull(rules, "rules");
        for (RankingCategory category : RankingCategory.playerCategories()) {
            playerBoards.put(category, new Leaderboard<>());
        }
    }

    /**
     * Restores a board after a restart. While the season is open every player score is recomputed from the stored
     * stats with {@code rules} (stored timestamps keep the tie order); a frozen board restores its final scores as
     * stored. Guild scores and time-attack runs are restored as stored. The result is not dirty.
     */
    public static RankingBoard rebuild(RankingSnapshot snapshot, RankingRules rules) {
        Objects.requireNonNull(snapshot, "snapshot");
        RankingBoard board = new RankingBoard(snapshot.seasonId(), rules);
        for (PlayerStanding player : snapshot.players()) {
            board.standings.put(player.playerId(),
                    new Standing(player.name(), player.stats(), player.updatedAtMillis()));
            for (RankingCategory category : RankingCategory.playerCategories()) {
                PlayerStanding.RankedScore stored = player.scores().get(category);
                if (snapshot.frozen()) {
                    if (stored != null) {
                        board.place(category, player.playerId(), player.name(), stored.score(), stored.reachedAtMillis());
                    }
                } else {
                    long score = RankingCalculator.score(category, player.stats(), rules);
                    long reachedAt = stored != null ? stored.reachedAtMillis() : player.updatedAtMillis();
                    board.place(category, player.playerId(), player.name(), score, reachedAt);
                }
            }
        }
        for (GuildStanding guild : snapshot.guilds()) {
            board.guilds.update(guild.guildId(), guild.name(), guild.score(), guild.updatedAtMillis());
        }
        board.timeAttack.restore(snapshot.timeAttack());
        board.frozen = snapshot.frozen();
        return board;
    }

    // ------------------------------------------------------------------------------------------------- changes

    /**
     * Records a player's current season stats (call when {@link SeasonStatsTracker#revision()} changed, e.g. at most
     * once per second, and when the player logs in or out).
     *
     * @param name      current display name (1 to 64 characters)
     * @param nowMillis server wall clock; becomes the tie-break time of every category whose score changed
     * @return {@code false} if the board is frozen or nothing changed
     */
    public boolean updatePlayer(UUID playerId, String name, SeasonStats stats, long nowMillis) {
        Objects.requireNonNull(playerId, "playerId");
        Check.displayName("name", name);
        Objects.requireNonNull(stats, "stats");
        Check.nonNegative("nowMillis", nowMillis);
        if (frozen) {
            return false;
        }
        Standing current = standings.get(playerId);
        boolean sameStats = current != null && current.stats.equals(stats);
        if (sameStats && current.name.equals(name)) {
            return false;
        }
        standings.put(playerId, new Standing(name, stats, sameStats ? current.updatedAtMillis : nowMillis));
        for (RankingCategory category : RankingCategory.playerCategories()) {
            place(category, playerId, name, RankingCalculator.score(category, stats, rules), nowMillis);
        }
        revision++;
        return true;
    }

    /** Removes a player from every ranking (operator action; allowed while frozen). Time-attack runs stay. */
    public boolean removePlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (standings.remove(playerId) == null) {
            return false;
        }
        for (Leaderboard<UUID> board : playerBoards.values()) {
            board.remove(playerId);
        }
        revision++;
        return true;
    }

    /**
     * Sets a guild's score, normally {@link RankingCalculator#guildScore} over its current members (call when a
     * member's contribution or the membership changes). A score of 0 removes the guild from the ranking.
     *
     * @return {@code false} if the board is frozen or nothing changed
     */
    public boolean updateGuild(String guildId, String name, long score, long nowMillis) {
        Check.guildId(guildId);
        Check.displayName("name", name);
        Check.nonNegative("score", score);
        Check.nonNegative("nowMillis", nowMillis);
        if (frozen) {
            return false;
        }
        boolean changed = score == 0 ? guilds.remove(guildId) : guilds.update(guildId, name, score, nowMillis);
        if (changed) {
            revision++;
        }
        return changed;
    }

    /** Removes a guild (disbanded, or operator action; allowed while frozen). */
    public boolean removeGuild(String guildId) {
        boolean removed = guilds.remove(Objects.requireNonNull(guildId, "guildId"));
        if (removed) {
            revision++;
        }
        return removed;
    }

    /** Validates and records a boss clear; rejected with {@link TimeAttackValidator#SEASON_CLOSED} once frozen. */
    public TimeAttackSubmission submitTimeAttack(TimeAttackRun run, TimeAttackRules rules) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(rules, "rules");
        if (frozen) {
            return TimeAttackSubmission.rejected(
                    TimeAttackValidator.Validation.rejected(List.of(TimeAttackValidator.SEASON_CLOSED)));
        }
        TimeAttackSubmission submission = timeAttack.submit(run, rules);
        if (submission.partyBest() || submission.firstClear()) {
            revision++;
        }
        return submission;
    }

    /** Operator removal of a time-attack run (allowed while frozen), see {@link TimeAttackBoard}. */
    public boolean removeTimeAttackRun(UUID runId) {
        boolean removed = timeAttack.removeRun(runId);
        if (removed) {
            revision++;
        }
        return removed;
    }

    /**
     * Applies new rules (config reload). An open board recomputes every player score from the stored stats; tie
     * timestamps are kept. A frozen board keeps its final scores.
     */
    public void setRules(RankingRules newRules) {
        Objects.requireNonNull(newRules, "rules");
        if (newRules.equals(rules)) {
            return;
        }
        rules = newRules;
        if (frozen) {
            return;
        }
        for (RankingCategory category : RankingCategory.playerCategories()) {
            Leaderboard<UUID> board = playerBoards.get(category);
            Map<UUID, Long> reachedAt = new HashMap<>();
            for (UUID playerId : standings.keySet()) {
                OptionalLong time = board.updatedAtOf(playerId);
                if (time.isPresent()) {
                    reachedAt.put(playerId, time.getAsLong());
                }
            }
            board.clear();
            for (Map.Entry<UUID, Standing> entry : standings.entrySet()) {
                Standing standing = entry.getValue();
                long score = RankingCalculator.score(category, standing.stats, rules);
                place(category, entry.getKey(), standing.name, score,
                        reachedAt.getOrDefault(entry.getKey(), standing.updatedAtMillis));
            }
        }
        revision++;
    }

    /**
     * Makes the current standings final (call on {@code SeasonEvent.Type.ENDED}, and after loading a season that has
     * already ended).
     *
     * @return {@code true} if the board was open
     */
    public boolean freeze() {
        if (frozen) {
            return false;
        }
        frozen = true;
        revision++;
        return true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    // ------------------------------------------------------------------------------------------------- reads

    public SeasonId seasonId() {
        return seasonId;
    }

    public RankingRules rules() {
        return rules;
    }

    /** @param category a player category ({@link RankingCategory#isPlayerCategory()}) */
    public List<RankingEntry<UUID>> top(RankingCategory category, int n) {
        return playerBoard(category).top(n);
    }

    public OptionalInt rankOf(RankingCategory category, UUID playerId) {
        return playerBoard(category).rankOf(playerId);
    }

    public Optional<RankingEntry<UUID>> entryOf(RankingCategory category, UUID playerId) {
        return playerBoard(category).entryOf(playerId);
    }

    /** Number of ranked players in a player category. */
    public int size(RankingCategory category) {
        return playerBoard(category).size();
    }

    public List<RankingEntry<String>> topGuilds(int n) {
        return guilds.top(n);
    }

    public OptionalInt guildRankOf(String guildId) {
        return guilds.rankOf(guildId);
    }

    public Optional<RankingEntry<String>> guildEntryOf(String guildId) {
        return guilds.entryOf(guildId);
    }

    public int guildCount() {
        return guilds.size();
    }

    /** The time-attack rankings (read access; record clears with {@link #submitTimeAttack}). */
    public TimeAttackBoard timeAttack() {
        return timeAttack;
    }

    /** The last stats recorded for the player (also for offline players). */
    public Optional<SeasonStats> statsOf(UUID playerId) {
        Standing standing = standings.get(Objects.requireNonNull(playerId, "playerId"));
        return standing == null ? Optional.empty() : Optional.of(standing.stats);
    }

    // ------------------------------------------------------------------------------------------------- persistence

    public RankingSnapshot snapshot() {
        List<UUID> playerIds = new ArrayList<>(standings.keySet());
        playerIds.sort(Comparator.naturalOrder());
        List<PlayerStanding> players = new ArrayList<>(playerIds.size());
        for (UUID playerId : playerIds) {
            Standing standing = standings.get(playerId);
            Map<RankingCategory, PlayerStanding.RankedScore> scores = new EnumMap<>(RankingCategory.class);
            for (RankingCategory category : RankingCategory.playerCategories()) {
                Leaderboard<UUID> board = playerBoards.get(category);
                Optional<RankingEntry<UUID>> entry = board.entryOf(playerId);
                if (entry.isPresent()) {
                    scores.put(category, new PlayerStanding.RankedScore(entry.get().score(),
                            board.updatedAtOf(playerId).orElseThrow()));
                }
            }
            players.add(new PlayerStanding(playerId, standing.name, standing.stats, standing.updatedAtMillis, scores));
        }
        List<GuildStanding> guildStandings = new ArrayList<>(guilds.size());
        for (RankingEntry<String> entry : guilds.top(guilds.size())) {
            guildStandings.add(new GuildStanding(entry.id(), entry.name(), entry.score(),
                    guilds.updatedAtOf(entry.id()).orElseThrow()));
        }
        return new RankingSnapshot(seasonId, frozen, players, guildStandings, timeAttack.snapshot());
    }

    /**
     * The season's permanent record: the top {@code topN} of every score category, the record run of every boss,
     * difficulty and bracket, and the final boss's first clear. Freeze the board first so the archive is final.
     */
    public SeasonArchive archive(Instant archivedAt, int topN) {
        Objects.requireNonNull(archivedAt, "archivedAt");
        Check.range("topN", topN, 1, MAX_ARCHIVE_SIZE);
        Map<RankingCategory, List<RankingEntry<String>>> rankings = new EnumMap<>(RankingCategory.class);
        for (RankingCategory category : RankingCategory.playerCategories()) {
            List<RankingEntry<String>> entries = new ArrayList<>();
            for (RankingEntry<UUID> entry : playerBoards.get(category).top(topN)) {
                entries.add(new RankingEntry<>(entry.rank(), entry.id().toString(), entry.name(), entry.score()));
            }
            rankings.put(category, entries);
        }
        rankings.put(RankingCategory.GUILD, guilds.top(topN));
        return new SeasonArchive(seasonId, archivedAt, rankings, timeAttack.bests(),
                timeAttack.firstClear(TimeAttackRules.FINAL_BOSS_ID).orElse(null));
    }

    /** Incremented by every change that must be persisted. */
    public long revision() {
        return revision;
    }

    public boolean isDirty() {
        return revision > savedRevision;
    }

    /** Records that the snapshot taken at {@code persistedRevision} has been stored. Never moves backwards. */
    public void markSaved(long persistedRevision) {
        if (persistedRevision > savedRevision) {
            savedRevision = persistedRevision;
        }
    }

    // ------------------------------------------------------------------------------------------------- internals

    private Leaderboard<UUID> playerBoard(RankingCategory category) {
        Objects.requireNonNull(category, "category");
        Leaderboard<UUID> board = playerBoards.get(category);
        if (board == null) {
            throw new IllegalArgumentException(category.id() + " is not a player ranking"
                    + (category == RankingCategory.GUILD ? " (use the guild methods)" : " (use timeAttack())"));
        }
        return board;
    }

    private void place(RankingCategory category, UUID playerId, String name, long score, long reachedAtMillis) {
        Leaderboard<UUID> board = playerBoards.get(category);
        if (score == 0) {
            board.remove(playerId);
        } else {
            board.update(playerId, name, score, reachedAtMillis);
        }
    }
}
