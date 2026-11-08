package com.eternalrealm.ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Boss time-attack rankings of one season.
 *
 * <p><b>What is kept:</b> for every boss and difficulty, the best accepted run of every distinct party (set of
 * members). A party that clears a boss fifty times appears once, with its best time, instead of filling the ranking.
 * A player's personal best per boss and difficulty is always one of these runs (the fastest run containing the player
 * is also the fastest run of its own party), so personal bests need no extra storage. The earliest accepted clear of
 * every boss is kept separately (it is a hall-of-fame record even after the party improves its time).</p>
 *
 * <p><b>Rankings</b> are per boss, difficulty and {@link PartyBracket}, fastest first ({@link #FASTEST_FIRST}: duration
 * in ticks, then the earlier clear, then the run id), so every run has its own rank.</p>
 *
 * <p>Changes go through {@link RankingBoard}, which owns the board and stops changes once the season's rankings are
 * frozen; this class offers the read side publicly. Not thread-safe: server thread only.</p>
 */
public final class TimeAttackBoard {
    /** Ranking order: shorter duration, then earlier clear (wall clock), then run id. */
    public static final Comparator<TimeAttackRun> FASTEST_FIRST = Comparator
            .comparingLong(TimeAttackRun::durationTicks)
            .thenComparingLong(TimeAttackRun::clearedAtMillis)
            .thenComparing(TimeAttackRun::runId);
    /** First-clear order: earlier clear, then run id. Ticks are not used: they restart with the server. */
    static final Comparator<TimeAttackRun> EARLIEST_FIRST = Comparator
            .comparingLong(TimeAttackRun::clearedAtMillis)
            .thenComparing(TimeAttackRun::runId);
    /** Deterministic order for storage and archives: boss, difficulty, bracket, then fastest first. */
    static final Comparator<TimeAttackRun> STORAGE_ORDER = Comparator
            .comparing(TimeAttackRun::bossId)
            .thenComparing(TimeAttackRun::difficulty)
            .thenComparing(TimeAttackRun::bracket)
            .thenComparing(FASTEST_FIRST);

    private record BoardKey(String bossId, BossDifficulty difficulty, PartyBracket bracket) {
    }

    private record PartyKey(String bossId, BossDifficulty difficulty, Set<UUID> members) {
    }

    private record PersonalKey(UUID playerId, String bossId, BossDifficulty difficulty) {
    }

    private final Map<BoardKey, TreeSet<TimeAttackRun>> boards = new HashMap<>();
    private final Map<PartyKey, TimeAttackRun> partyBests = new HashMap<>();
    private final Map<UUID, TimeAttackRun> runsById = new HashMap<>();
    private final Map<PersonalKey, TimeAttackRun> personalBests = new HashMap<>();
    private final Map<String, TimeAttackRun> firstClears = new HashMap<>();

    // ------------------------------------------------------------------------------------------------- reads

    /** The {@code n} fastest party bests of a boss, difficulty and bracket, ranked from 1. */
    public List<TimeAttackEntry> top(String bossId, BossDifficulty difficulty, PartyBracket bracket, int n) {
        Check.range("n", n, 0, Integer.MAX_VALUE);
        TreeSet<TimeAttackRun> board = boards.get(boardKey(bossId, difficulty, bracket));
        if (board == null || n == 0) {
            return List.of();
        }
        List<TimeAttackEntry> entries = new ArrayList<>(Math.min(n, board.size()));
        Iterator<TimeAttackRun> iterator = board.iterator();
        while (iterator.hasNext() && entries.size() < n) {
            entries.add(new TimeAttackEntry(entries.size() + 1, iterator.next()));
        }
        return Collections.unmodifiableList(entries);
    }

    /** The record run of a boss, difficulty and bracket. */
    public Optional<TimeAttackRun> best(String bossId, BossDifficulty difficulty, PartyBracket bracket) {
        TreeSet<TimeAttackRun> board = boards.get(boardKey(bossId, difficulty, bracket));
        return board == null ? Optional.empty() : Optional.of(board.first());
    }

    /** The record run of every boss, difficulty and bracket that has one, in {@link #STORAGE_ORDER}. */
    public List<TimeAttackRun> bests() {
        List<TimeAttackRun> bests = new ArrayList<>();
        for (TreeSet<TimeAttackRun> board : boards.values()) {
            bests.add(board.first());
        }
        bests.sort(STORAGE_ORDER);
        return Collections.unmodifiableList(bests);
    }

    /** The fastest accepted run of the player for this boss and difficulty, in any bracket. */
    public Optional<TimeAttackRun> personalBest(UUID playerId, String bossId, BossDifficulty difficulty) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(bossId, "bossId");
        Objects.requireNonNull(difficulty, "difficulty");
        return Optional.ofNullable(personalBests.get(new PersonalKey(playerId, bossId, difficulty)));
    }

    /** Every personal best of the player, in {@link #STORAGE_ORDER}. */
    public List<TimeAttackRun> personalBests(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        List<TimeAttackRun> bests = new ArrayList<>();
        for (Map.Entry<PersonalKey, TimeAttackRun> entry : personalBests.entrySet()) {
            if (entry.getKey().playerId().equals(playerId)) {
                bests.add(entry.getValue());
            }
        }
        bests.sort(STORAGE_ORDER);
        return Collections.unmodifiableList(bests);
    }

    /** The season's earliest accepted clear of the boss (any difficulty and bracket). */
    public Optional<TimeAttackRun> firstClear(String bossId) {
        return Optional.ofNullable(firstClears.get(Objects.requireNonNull(bossId, "bossId")));
    }

    /** Rank of a stored run within its boss, difficulty and bracket; empty if the run is not (or no longer) kept. */
    public OptionalInt rankOf(UUID runId) {
        TimeAttackRun run = runsById.get(Objects.requireNonNull(runId, "runId"));
        if (run == null) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(boards.get(boardKey(run)).headSet(run).size() + 1);
    }

    /** Number of kept runs (party bests). */
    public int size() {
        return runsById.size();
    }

    // ------------------------------------------------------------------------------------------------- changes

    /** Validates and records a run. Package-private: changes go through {@link RankingBoard}. */
    TimeAttackSubmission submit(TimeAttackRun run, TimeAttackRules rules) {
        TimeAttackValidator.Validation validation = TimeAttackValidator.validate(run, rules);
        if (!validation.accepted()) {
            return TimeAttackSubmission.rejected(validation);
        }
        return record(run, validation, true);
    }

    /**
     * Removes a run (operator correction, e.g. a cheat found later). Personal bests of its members fall back to their
     * next best kept run. If it was a first clear, the earliest remaining kept run of the boss becomes the first
     * clear; runs that were never a party best are not kept, so this is the earliest <em>known</em> clear.
     *
     * @return {@code true} if the run was kept as a party best or a first clear
     */
    boolean removeRun(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        boolean removed = false;
        TimeAttackRun run = runsById.remove(runId);
        if (run != null) {
            BoardKey key = boardKey(run);
            TreeSet<TimeAttackRun> board = boards.get(key);
            board.remove(run);
            if (board.isEmpty()) {
                boards.remove(key);
            }
            partyBests.remove(partyKey(run));
            for (TimeAttackMember member : run.members()) {
                recomputePersonalBest(member.id(), run.bossId(), run.difficulty());
            }
            removed = true;
        }
        for (TimeAttackRun first : List.copyOf(firstClears.values())) {
            if (first.runId().equals(runId)) {
                firstClears.remove(first.bossId());
                runsById.values().stream()
                        .filter(candidate -> candidate.bossId().equals(first.bossId()))
                        .min(EARLIEST_FIRST)
                        .ifPresent(candidate -> firstClears.put(candidate.bossId(), candidate));
                removed = true;
            }
        }
        return removed;
    }

    /** Replaces the whole state with a stored one (runs are not re-validated against the current rules). */
    void restore(TimeAttackSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        clear();
        for (TimeAttackRun run : snapshot.bestRuns()) {
            record(run, TimeAttackValidator.Validation.ACCEPTED, false);
        }
        for (TimeAttackRun run : snapshot.firstClears()) {
            firstClears.put(run.bossId(), run);
        }
    }

    TimeAttackSnapshot snapshot() {
        List<TimeAttackRun> runs = new ArrayList<>(runsById.values());
        runs.sort(STORAGE_ORDER);
        List<TimeAttackRun> firsts = new ArrayList<>(firstClears.values());
        firsts.sort(Comparator.comparing(TimeAttackRun::bossId));
        return new TimeAttackSnapshot(runs, firsts);
    }

    void clear() {
        boards.clear();
        partyBests.clear();
        runsById.clear();
        personalBests.clear();
        firstClears.clear();
    }

    // ------------------------------------------------------------------------------------------------- internals

    private TimeAttackSubmission record(TimeAttackRun run, TimeAttackValidator.Validation validation,
                                        boolean offerFirstClear) {
        requireUnusedId(run);
        boolean firstClear = offerFirstClear && offerFirstClear(run);
        PartyKey partyKey = partyKey(run);
        TimeAttackRun previous = partyBests.get(partyKey);
        if (previous != null && FASTEST_FIRST.compare(run, previous) >= 0) {
            return new TimeAttackSubmission(validation, false, false, List.of(), firstClear);
        }
        TreeSet<TimeAttackRun> board = boards.computeIfAbsent(boardKey(run), key -> new TreeSet<>(FASTEST_FIRST));
        if (previous != null) {
            board.remove(previous);
            runsById.remove(previous.runId());
        }
        board.add(run);
        partyBests.put(partyKey, run);
        runsById.put(run.runId(), run);
        List<UUID> improved = new ArrayList<>();
        for (TimeAttackMember member : run.members()) {
            PersonalKey key = new PersonalKey(member.id(), run.bossId(), run.difficulty());
            TimeAttackRun personal = personalBests.get(key);
            if (personal == null || FASTEST_FIRST.compare(run, personal) < 0) {
                personalBests.put(key, run);
                improved.add(member.id());
            }
        }
        return new TimeAttackSubmission(validation, board.first().equals(run), true, improved, firstClear);
    }

    private boolean offerFirstClear(TimeAttackRun run) {
        TimeAttackRun current = firstClears.get(run.bossId());
        if (current == null || EARLIEST_FIRST.compare(run, current) < 0) {
            firstClears.put(run.bossId(), run);
            return true;
        }
        return false;
    }

    /** A run id identifies one run; reusing it for a different run is an integration bug. Resubmitting is fine. */
    private void requireUnusedId(TimeAttackRun run) {
        TimeAttackRun kept = runsById.get(run.runId());
        if (kept != null && !kept.equals(run)) {
            throw new IllegalArgumentException("Run id " + run.runId() + " is already used by another run");
        }
        for (TimeAttackRun first : firstClears.values()) {
            if (first.runId().equals(run.runId()) && !first.equals(run)) {
                throw new IllegalArgumentException("Run id " + run.runId() + " is already used by another run");
            }
        }
    }

    private void recomputePersonalBest(UUID playerId, String bossId, BossDifficulty difficulty) {
        PersonalKey key = new PersonalKey(playerId, bossId, difficulty);
        Optional<TimeAttackRun> best = runsById.values().stream()
                .filter(run -> run.bossId().equals(bossId) && run.difficulty() == difficulty && run.hasMember(playerId))
                .min(FASTEST_FIRST);
        if (best.isPresent()) {
            personalBests.put(key, best.get());
        } else {
            personalBests.remove(key);
        }
    }

    private static BoardKey boardKey(String bossId, BossDifficulty difficulty, PartyBracket bracket) {
        Objects.requireNonNull(bossId, "bossId");
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(bracket, "bracket");
        return new BoardKey(bossId, difficulty, bracket);
    }

    private static BoardKey boardKey(TimeAttackRun run) {
        return new BoardKey(run.bossId(), run.difficulty(), run.bracket());
    }

    private static PartyKey partyKey(TimeAttackRun run) {
        return new PartyKey(run.bossId(), run.difficulty(), run.memberIds());
    }
}
