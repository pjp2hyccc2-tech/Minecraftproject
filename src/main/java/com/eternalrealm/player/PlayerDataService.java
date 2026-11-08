package com.eternalrealm.player;

import com.eternalrealm.character.AllocationResult;
import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.CharacterRepository;
import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceResult;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.character.PlayerCharacter;
import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.character.StatType;
import com.eternalrealm.core.IncidentIds;
import com.eternalrealm.core.PeriodicTrigger;
import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Owns the lifecycle of per-player persistent data (Phase 1: account + season character):
 * load on join, server-authoritative mutations, autosave, save on leave and flush on shutdown.
 *
 * <h2>Threading model</h2>
 * <ul>
 *     <li>Every public method must be called on the server thread; all state of this class is confined to it.</li>
 *     <li>Blocking repository calls run on a {@link KeyedSerialExecutor} keyed by player UUID, so the I/O of one
 *         player is strictly ordered (a save on logout always completes before the next load on login).</li>
 *     <li>I/O results are handed back through {@code mainThread}. The hand-off is posted <em>inside</em> the I/O
 *         task, which guarantees that the completions of one player run in submission order.</li>
 * </ul>
 *
 * <h2>Data safety rules</h2>
 * <ul>
 *     <li>A failed load never produces a fresh character; the player is rejected via
 *         {@link PlayerDataListener#onLoadFailed}.</li>
 *     <li>Saves are retried. If the save of a player who already left still fails, the record is kept in memory,
 *         preferred over (stale) disk data on the next login and retried by autosave.</li>
 * </ul>
 */
public final class PlayerDataService {
    private static final Logger LOG = LoggerFactory.getLogger(PlayerDataService.class);
    static final int SAVE_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 100L;

    private final AccountRepository accounts;
    private final CharacterRepository characters;
    private final KeyedSerialExecutor<UUID> io;
    private final Executor mainThread;
    private final Clock clock;
    private final SeasonId season;
    private final PlayerDataListener listener;
    private final PeriodicTrigger autosave;

    private final PlayerSessionRegistry sessions = new PlayerSessionRegistry();
    private final Map<UUID, PlayerCharacter> active = new HashMap<>();
    private final Map<UUID, CharacterRecord> unsaved = new HashMap<>();
    private final Set<UUID> savesInFlight = new HashSet<>();
    private final List<PendingSave> pendingSaves = new ArrayList<>();
    private CharacterRules rules;
    private boolean shuttingDown;

    public PlayerDataService(AccountRepository accounts, CharacterRepository characters, KeyedSerialExecutor<UUID> io,
                             Executor mainThread, Clock clock, SeasonId season, CharacterRules rules,
                             Duration autosaveInterval, PlayerDataListener listener) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.io = Objects.requireNonNull(io, "io");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.season = Objects.requireNonNull(season, "season");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.autosave = new PeriodicTrigger(autosaveInterval, clock);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Session lifecycle
    // ------------------------------------------------------------------------------------------------------------

    /** Opens a session and starts loading the player's data asynchronously. */
    public void onPlayerJoin(UUID playerId, String name) {
        Objects.requireNonNull(playerId, "playerId");
        if (shuttingDown) {
            LOG.warn("Ignoring join of {} during shutdown", playerId);
            return;
        }
        PlayerSession previous = sessions.remove(playerId);
        if (previous != null) {
            LOG.warn("Player {} joined while a previous session was still open; closing the old session first", playerId);
            closeSession(previous);
        }
        Instant now = clock.instant();
        PlayerSession session = sessions.open(playerId, AccountRecord.sanitizeName(name), now);
        long generation = session.generation();
        String safeName = session.name();
        io.submit(playerId, () -> {
            LoadOutcome outcome = loadBlocking(playerId, safeName, now);
            mainThread.execute(() -> completeLoad(playerId, generation, outcome));
            return null;
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                // Only reachable if the executor rejected the task; never leave the session stuck in LOADING.
                mainThread.execute(() -> completeLoad(playerId, generation, LoadOutcome.failure(error)));
            }
        });
    }

    /** Closes the session; a dirty character is saved asynchronously. */
    public void onPlayerLeave(UUID playerId) {
        PlayerSession session = sessions.remove(playerId);
        if (session != null) {
            closeSession(session);
        }
    }

    private void closeSession(PlayerSession session) {
        PlayerCharacter character = active.remove(session.playerId());
        if (character != null && character.isDirty()) {
            submitSave(character.toRecord(clock.instant()), character.revision(), null);
        }
    }

    /** Runs on the I/O executor. */
    private LoadOutcome loadBlocking(UUID playerId, String name, Instant now) {
        try {
            AccountRecord account = accounts.load(playerId)
                    .map(existing -> existing.withLogin(name, now))
                    .orElseGet(() -> AccountRecord.firstLogin(playerId, name, now));
            accounts.save(account);
            return LoadOutcome.success(characters.load(playerId, season).orElse(null));
        } catch (DataAccessException | RuntimeException e) {
            return LoadOutcome.failure(e);
        }
    }

    private void completeLoad(UUID playerId, long generation, LoadOutcome outcome) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || session.generation() != generation || session.state() != SessionState.LOADING) {
            LOG.debug("Discarding stale load result for {} (generation {})", playerId, generation);
            return;
        }
        if (outcome.error() != null) {
            String incident = IncidentIds.next();
            LOG.error("[incident {}] Could not load data of {} ({}); the player will be disconnected",
                    incident, session.name(), playerId, outcome.error());
            session.markFailed();
            listener.onLoadFailed(playerId, incident);
            return;
        }
        PlayerCharacter character;
        CharacterRecord pending = unsaved.remove(playerId);
        if (pending != null) {
            LOG.warn("Using in-memory data of {} because its last save failed", playerId);
            character = PlayerCharacter.fromRecord(pending, rules);
            character.markDirty();
        } else if (outcome.character() != null) {
            character = PlayerCharacter.fromRecord(outcome.character(), rules);
            if (character.isDirty()) {
                LOG.info("Character of {} was adjusted to the current rules", playerId);
            }
        } else {
            character = PlayerCharacter.createNew(playerId, season, clock.instant());
            LOG.info("Created a new character for {} ({}) in season {}", session.name(), playerId, season);
        }
        active.put(playerId, character);
        session.markActive();
        listener.onCharacterReady(playerId);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------------------------

    public Optional<SessionState> sessionState(UUID playerId) {
        PlayerSession session = sessions.get(playerId);
        return session == null ? Optional.empty() : Optional.of(session.state());
    }

    public boolean isActive(UUID playerId) {
        return active.containsKey(playerId);
    }

    public Optional<CharacterSnapshot> snapshot(UUID playerId) {
        PlayerCharacter character = active.get(playerId);
        return character == null ? Optional.empty() : Optional.of(character.snapshot(rules));
    }

    public SeasonId season() {
        return season;
    }

    public CharacterRules rules() {
        return rules;
    }

    public Status status() {
        return new Status(sessions.size(), sessions.count(SessionState.LOADING), active.size(), unsaved.size(),
                savesInFlight.size());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Server-authoritative mutations
    // ------------------------------------------------------------------------------------------------------------

    /**
     * @param amount must be positive
     * @return the result, or empty if the player's data is not loaded
     */
    public Optional<ExperienceResult> grantExperience(UUID playerId, long amount, ExperienceSource source) {
        Objects.requireNonNull(source, "source");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return Optional.empty();
        }
        ExperienceResult result = character.addExperience(amount, rules);
        if (result.applied() > 0) {
            listener.onCharacterChanged(playerId);
        }
        if (result.leveledUp()) {
            LOG.info("{} reached level {} (+{} EXP, source {})", playerId, result.newLevel(), result.applied(), source);
            listener.onLevelUp(playerId, result);
        } else {
            LOG.debug("{} gained {} EXP (source {})", playerId, result.applied(), source);
        }
        return Optional.of(result);
    }

    public AllocationResult allocateStat(UUID playerId, StatType stat, int amount) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return AllocationResult.NOT_READY;
        }
        AllocationResult result = character.allocate(stat, amount, rules);
        if (result.isSuccess()) {
            listener.onCharacterChanged(playerId);
        }
        return result;
    }

    /** @return {@code false} if the data is not loaded or nothing was allocated */
    public boolean resetStatAllocation(UUID playerId) {
        PlayerCharacter character = active.get(playerId);
        if (character == null || !character.resetAllocation()) {
            return false;
        }
        listener.onCharacterChanged(playerId);
        return true;
    }

    public SetLevelResult setLevel(UUID playerId, int level) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return SetLevelResult.NOT_READY;
        }
        SetLevelResult result = character.setLevel(level, rules);
        if (result == SetLevelResult.SUCCESS) {
            listener.onCharacterChanged(playerId);
        }
        return result;
    }

    /** Applies reloaded rules and re-establishes invariants of every loaded character. */
    public void updateRules(CharacterRules newRules) {
        this.rules = Objects.requireNonNull(newRules, "newRules");
        for (Map.Entry<UUID, PlayerCharacter> entry : active.entrySet()) {
            if (entry.getValue().reconcile(newRules)) {
                LOG.info("Character of {} was adjusted to the reloaded rules", entry.getKey());
            }
            listener.onCharacterChanged(entry.getKey());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------------------------------------------------

    /** Call once per server tick. */
    public void tick() {
        if (shuttingDown) {
            return;
        }
        pendingSaves.removeIf(pendingSave -> pendingSave.future().isDone());
        if (autosave.poll()) {
            int queued = saveAllDirty();
            if (queued > 0) {
                LOG.debug("Autosave queued {} save(s)", queued);
            }
        }
    }

    public void setAutosaveInterval(Duration interval) {
        autosave.setInterval(interval);
    }

    /** Queues saves for every dirty character and retries unsaved records. @return number of saves queued */
    public int saveAllDirty() {
        if (shuttingDown) {
            return 0;
        }
        int queued = 0;
        Instant now = clock.instant();
        for (Map.Entry<UUID, PlayerCharacter> entry : active.entrySet()) {
            PlayerCharacter character = entry.getValue();
            if (character.isDirty() && !savesInFlight.contains(entry.getKey())) {
                submitSave(character.toRecord(now), character.revision(), character);
                queued++;
            }
        }
        for (CharacterRecord record : List.copyOf(unsaved.values())) {
            if (!savesInFlight.contains(record.playerId())) {
                submitSave(record, 0L, null);
                queued++;
            }
        }
        return queued;
    }

    /**
     * @param owner the live character the record was taken from, or {@code null} if no live character holds it
     *              (logout, retry of an unsaved record, shutdown)
     */
    private CompletableFuture<Boolean> submitSave(CharacterRecord record, long revision, PlayerCharacter owner) {
        UUID playerId = record.playerId();
        savesInFlight.add(playerId);
        CompletableFuture<Boolean> future = io.submit(playerId, () -> {
            boolean ok = saveBlocking(record);
            mainThread.execute(() -> completeSave(record, revision, owner, ok));
            return ok;
        });
        future.whenComplete((ok, error) -> {
            if (error != null) {
                LOG.error("Save task of {} could not run. UNSAVED STATE: {}", playerId, record, error);
            }
        });
        pendingSaves.add(new PendingSave(record, future));
        return future;
    }

    /** Runs on the I/O executor. */
    private boolean saveBlocking(CharacterRecord record) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt++) {
            try {
                characters.save(record);
                return true;
            } catch (DataAccessException | RuntimeException e) {
                if (attempt == SAVE_ATTEMPTS) {
                    LOG.error("Saving character {} failed after {} attempts. UNSAVED STATE: {}",
                            record.playerId(), attempt, record, e);
                    return false;
                }
                LOG.warn("Saving character {} failed (attempt {}/{}): {}", record.playerId(), attempt, SAVE_ATTEMPTS,
                        e.toString());
                try {
                    Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    LOG.error("Interrupted while saving {}. UNSAVED STATE: {}", record.playerId(), record);
                    return false;
                }
            }
        }
        return false;
    }

    private void completeSave(CharacterRecord record, long revision, PlayerCharacter owner, boolean ok) {
        UUID playerId = record.playerId();
        savesInFlight.remove(playerId);
        PlayerCharacter live = active.get(playerId);
        if (ok) {
            if (owner != null && live == owner) {
                owner.markSaved(revision);
            }
            unsaved.remove(playerId, record);
            return;
        }
        if (live != null) {
            // The live character still holds this state (or newer) and stays dirty: autosave retries it.
            return;
        }
        // Nobody holds this data any more. Keep it so it is neither lost nor shadowed by stale disk data.
        CharacterRecord existing = unsaved.get(playerId);
        if (existing == null || !existing.updatedAt().isAfter(record.updatedAt())) {
            unsaved.put(playerId, record);
        }
    }

    /**
     * Flushes everything and waits for the I/O queue. Called on the server thread after the game loop stopped,
     * so no completion callback will run any more; results are read from the futures directly.
     */
    public ShutdownReport shutdown(Duration timeout) {
        if (shuttingDown) {
            return new ShutdownReport(0, 0, 0, 0, true);
        }
        Instant now = clock.instant();
        for (PlayerCharacter character : active.values()) {
            if (character.isDirty()) {
                submitSave(character.toRecord(now), character.revision(), null);
            }
        }
        for (CharacterRecord record : unsaved.values()) {
            submitSave(record, 0L, null);
        }
        shuttingDown = true;
        active.clear();
        unsaved.clear();
        sessions.clear();
        savesInFlight.clear();

        boolean drained;
        try {
            drained = io.awaitQuiescence(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            drained = false;
        }
        int saved = 0;
        int failed = 0;
        int pending = 0;
        for (PendingSave pendingSave : pendingSaves) {
            CompletableFuture<Boolean> future = pendingSave.future();
            if (!future.isDone()) {
                pending++;
                LOG.error("Save of {} did not finish before the shutdown timeout. UNSAVED STATE: {}",
                        pendingSave.record().playerId(), pendingSave.record());
            } else if (future.isCompletedExceptionally() || !Boolean.TRUE.equals(future.getNow(Boolean.FALSE))) {
                failed++;
            } else {
                saved++;
            }
        }
        ShutdownReport report = new ShutdownReport(pendingSaves.size(), saved, failed, pending, drained);
        pendingSaves.clear();
        if (report.clean()) {
            LOG.info("Player data flushed: {}", report);
        } else {
            LOG.error("Player data flush incomplete: {} (see UNSAVED STATE entries above)", report);
        }
        return report;
    }

    // ------------------------------------------------------------------------------------------------------------

    private record LoadOutcome(CharacterRecord character, Throwable error) {
        static LoadOutcome success(CharacterRecord character) {
            return new LoadOutcome(character, null);
        }

        static LoadOutcome failure(Throwable error) {
            return new LoadOutcome(null, error);
        }
    }

    private record PendingSave(CharacterRecord record, CompletableFuture<Boolean> future) {
    }

    /** Snapshot of the service state for operators ({@code /er admin status}). */
    public record Status(int sessions, long loading, int active, int unsavedInMemory, int savesInFlight) {
    }

    /**
     * @param tracked saves observed during the shutdown window
     * @param pending saves that had not finished when the timeout elapsed
     */
    public record ShutdownReport(int tracked, int saved, int failed, int pending, boolean drained) {
        public boolean clean() {
            return failed == 0 && pending == 0 && drained;
        }
    }
}
