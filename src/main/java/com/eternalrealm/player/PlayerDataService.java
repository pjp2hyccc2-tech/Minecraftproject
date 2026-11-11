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
import com.eternalrealm.job.JobRules;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.SkillTree;
import com.eternalrealm.season.SeasonId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 *     <li>Every save gets a sequence number. Saves are snapshotted on the server thread in program order, so a save
 *         with a higher number always contains the changes of every lower-numbered save of the same player.
 *         Failure handling is decided by these numbers, never by wall-clock time.</li>
 *     <li>Saves are retried. If the newest save of a player who already left still fails, the record is kept in memory,
 *         preferred over (stale) disk data on the next login and retried by autosave. An older failed save is ignored
 *         when a newer save of the same player is queued or has succeeded.</li>
 *     <li>At shutdown, a failed final save is retried once synchronously; what still cannot be saved is handed to the
 *         {@link EmergencyDump}.</li>
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
    private final EmergencyDump emergencyDump;
    private final PeriodicTrigger autosave;

    private final PlayerSessionRegistry sessions = new PlayerSessionRegistry();
    private final Map<UUID, PlayerCharacter> active = new HashMap<>();
    private final Map<UUID, UnsavedRecord> unsaved = new HashMap<>();
    private final Map<UUID, SaveTracker> saveTrackers = new HashMap<>();
    private final List<PendingSave> pendingSaves = new ArrayList<>();
    private long nextSaveSequence = 1;
    private CharacterRules rules;
    private boolean shuttingDown;

    public PlayerDataService(AccountRepository accounts, CharacterRepository characters, KeyedSerialExecutor<UUID> io,
                             Executor mainThread, Clock clock, SeasonId season, CharacterRules rules,
                             Duration autosaveInterval, PlayerDataListener listener, EmergencyDump emergencyDump) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.characters = Objects.requireNonNull(characters, "characters");
        this.io = Objects.requireNonNull(io, "io");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.season = Objects.requireNonNull(season, "season");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.listener = Objects.requireNonNull(listener, "listener");
        this.emergencyDump = Objects.requireNonNull(emergencyDump, "emergencyDump");
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
        UnsavedRecord pending = unsaved.remove(playerId);
        if (pending != null) {
            LOG.warn("Using in-memory data of {} because its last save failed", playerId);
            character = PlayerCharacter.fromRecord(pending.record(), rules);
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
        if (resetInvalidSkills(character)) {
            LOG.info("Skill points of {} were refunded because they no longer fit the job", playerId);
        }
        sanitizeLoadout(character);
        active.put(playerId, character);
        session.markActive();
        listener.onCharacterReady(playerId);
    }

    /**
     * Skills of another job, locked skills, skills whose prerequisites are missing or levels above the maximum are
     * refunded (job module rules).
     */
    private boolean resetInvalidSkills(PlayerCharacter character) {
        JobType job = JobType.fromStored(character.jobId());
        if (SkillTree.isValid(job, character.level(), character.skillInvestments())) {
            return false;
        }
        return character.resetSkills();
    }

    /** Drops loadout entries that are no longer valid (forgotten skills, another job). */
    private boolean sanitizeLoadout(PlayerCharacter character) {
        JobType job = JobType.fromStored(character.jobId());
        java.util.List<String> stored = character.skillLoadout();
        java.util.List<String> clean = SkillTree.sanitize(job, character.level(), character.skillInvestments(), stored);
        return clean != stored && character.setSkillLoadout(clean);
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
        long inFlight = saveTrackers.values().stream().filter(SaveTracker::inFlight).count();
        return new Status(sessions.size(), sessions.count(SessionState.LOADING), active.size(), unsaved.size(),
                (int) inFlight);
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
            if (resetInvalidSkills(character)) {
                LOG.info("Skill points of {} were refunded because they no longer fit the level", playerId);
            }
            sanitizeLoadout(character);
            listener.onCharacterChanged(playerId);
        }
        return result;
    }

    /** Applies reloaded rules and re-establishes invariants of every loaded character. */
    public void updateRules(CharacterRules newRules) {
        this.rules = Objects.requireNonNull(newRules, "newRules");
        for (Map.Entry<UUID, PlayerCharacter> entry : active.entrySet()) {
            if (entry.getValue().reconcile(newRules) | resetInvalidSkills(entry.getValue())
                    | sanitizeLoadout(entry.getValue())) {
                LOG.info("Character of {} was adjusted to the reloaded rules", entry.getKey());
            }
            listener.onCharacterChanged(entry.getKey());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Jobs, skills, gold, death penalty, module data (Phase 3)
    // ------------------------------------------------------------------------------------------------------------

    public enum JobChangeResult {
        OK,
        SAME_JOB,
        NOT_ENOUGH_GOLD,
        NOT_READY
    }

    /**
     * @param cost        gold paid
     * @param firstChoice the character had no job before (the platform layer hands out the starter kit)
     */
    public record JobChange(JobChangeResult result, long cost, boolean firstChoice) {
    }

    /** Chooses or changes the job, paying the change cost; invested skill points are refunded. */
    public JobChange chooseJob(UUID playerId, JobType job, JobRules jobRules) {
        Objects.requireNonNull(job, "job");
        if (!job.isChosen()) {
            throw new IllegalArgumentException("cannot choose " + job);
        }
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return new JobChange(JobChangeResult.NOT_READY, 0L, false);
        }
        JobType current = JobType.fromStored(character.jobId());
        if (current == job) {
            return new JobChange(JobChangeResult.SAME_JOB, 0L, false);
        }
        long cost = jobRules.changeCost(current, character.level());
        if (!character.spendGold(cost)) {
            return new JobChange(JobChangeResult.NOT_ENOUGH_GOLD, cost, false);
        }
        character.changeJob(job.id());
        LOG.info("{} changed job {} -> {} (cost {} gold)", playerId, current.id(), job.id(), cost);
        listener.onCharacterChanged(playerId);
        return new JobChange(JobChangeResult.OK, cost, !current.isChosen());
    }

    /**
     * Invests one skill point. A newly learned active or ultimate goes into the first free slot that takes it.
     *
     * @return empty if the data is not loaded
     */
    public Optional<SkillTree.InvestResult> investSkill(UUID playerId, String skillId) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return Optional.empty();
        }
        JobType job = JobType.fromStored(character.jobId());
        SkillTree.InvestResult result = SkillTree.canInvest(job, character.level(), character.skillInvestments(),
                character.unspentSkillPoints(rules), skillId);
        if (result == SkillTree.InvestResult.OK) {
            character.investSkillPoint(skillId, rules);
            com.eternalrealm.job.Jobs.skill(skillId).ifPresent(skill -> {
                java.util.List<String> effective = SkillTree.effectiveLoadout(job, character.level(),
                        character.skillInvestments(), character.skillLoadout());
                int slot = SkillTree.autoSlot(effective, skill);
                if (slot > 0) {
                    character.setSkillLoadout(SkillTree.assign(effective, slot, skill.id()));
                }
            });
            listener.onCharacterChanged(playerId);
        }
        return Optional.of(result);
    }

    /**
     * Puts a learned active into a skill slot ({@code ""} empties the slot); a skill already in another slot swaps
     * places with what was in {@code slot}.
     *
     * @return empty if the data is not loaded
     */
    public Optional<SkillTree.LoadoutResult> setSkillSlot(UUID playerId, int slot, String skillId) {
        Objects.requireNonNull(skillId, "skillId");
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return Optional.empty();
        }
        JobType job = JobType.fromStored(character.jobId());
        SkillTree.LoadoutResult result = SkillTree.canAssign(job, character.level(), character.skillInvestments(),
                slot, skillId);
        if (result == SkillTree.LoadoutResult.OK) {
            java.util.List<String> effective = SkillTree.effectiveLoadout(job, character.level(),
                    character.skillInvestments(), character.skillLoadout());
            if (character.setSkillLoadout(SkillTree.assign(effective, slot, skillId))) {
                listener.onCharacterChanged(playerId);
            }
        }
        return Optional.of(result);
    }

    public enum SkillResetResult {
        OK,
        NOTHING_TO_RESET,
        NOT_ENOUGH_GOLD,
        NOT_READY
    }

    /** @param cost the gold paid (or needed) */
    public record SkillReset(SkillResetResult result, long cost) {
    }

    /** Refunds every skill point for the job master's price (the skill loadout goes back to the default). */
    public SkillReset resetSkills(UUID playerId, JobRules jobRules) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return new SkillReset(SkillResetResult.NOT_READY, 0L);
        }
        if (character.skillInvestments().isEmpty()) {
            return new SkillReset(SkillResetResult.NOTHING_TO_RESET, 0L);
        }
        long cost = jobRules.skillResetCost(character.level());
        if (!character.spendGold(cost)) {
            return new SkillReset(SkillResetResult.NOT_ENOUGH_GOLD, cost);
        }
        character.resetSkills();
        LOG.info("{} reset their skill tree (cost {} gold)", playerId, cost);
        listener.onCharacterChanged(playerId);
        return new SkillReset(SkillResetResult.OK, cost);
    }

    /** Refunds stat and skill points (oblivion elixir). @return {@code false} if nothing was refunded */
    public boolean resetAllPoints(UUID playerId) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return false;
        }
        boolean changed = character.resetAllocation() | character.resetSkills();
        if (changed) {
            listener.onCharacterChanged(playerId);
        }
        return changed;
    }

    /**
     * The test kit (an operator's command, docs/modules/testkit.md): the level goes to the cap, the gold to the purse's
     * limit ({@link CharacterRecord#MAX_GOLD}), and every stat and skill point goes back into the pool, so all the
     * points of the cap can be spent anew (the skill loadout goes back to the job's default). Like {@link #setLevel},
     * nothing counts as EXP earned (the rankings do not move).
     *
     * @return the character afterwards, or empty if the data is not loaded
     */
    public Optional<CharacterSnapshot> maxOut(UUID playerId) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return Optional.empty();
        }
        character.setLevel(rules.levelCap(), rules);
        character.resetAllocation();
        character.resetSkills();
        character.addGold(CharacterRecord.MAX_GOLD);
        CharacterSnapshot snapshot = character.snapshot(rules);
        LOG.info("Test kit: {} is now level {} with {} gold, {} stat and {} skill points unspent", playerId,
                snapshot.level(), snapshot.gold(), snapshot.unspentStatPoints(), snapshot.unspentSkillPoints());
        listener.onCharacterChanged(playerId);
        return Optional.of(snapshot);
    }

    /** @return the gold actually added (saturating), or empty if the data is not loaded */
    public Optional<Long> grantGold(UUID playerId, long amount) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return Optional.empty();
        }
        long added = character.addGold(amount);
        if (added > 0) {
            listener.onCharacterChanged(playerId);
        }
        return Optional.of(added);
    }

    /** @return {@code false} if the data is not loaded or the balance does not cover {@code amount} */
    public boolean spendGold(UUID playerId, long amount) {
        PlayerCharacter character = active.get(playerId);
        if (character == null || !character.spendGold(amount)) {
            return false;
        }
        if (amount > 0) {
            listener.onCharacterChanged(playerId);
        }
        return true;
    }

    /** EXP earned this season (0 if the data is not loaded). */
    public long totalExperience(UUID playerId) {
        PlayerCharacter character = active.get(playerId);
        return character == null ? 0L : character.totalExperience();
    }

    public long gold(UUID playerId) {
        PlayerCharacter character = active.get(playerId);
        return character == null ? 0L : character.gold();
    }

    /** @return EXP lost (0 if the data is not loaded) */
    public long applyDeathPenalty(UUID playerId, double fraction) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return 0L;
        }
        long lost = character.applyDeathPenalty(fraction, rules);
        if (lost > 0) {
            listener.onCharacterChanged(playerId);
        }
        return lost;
    }

    /** Season data of another module; saved with the character. Ignored if the data is not loaded. */
    public boolean setExtension(UUID playerId, String key, String json) {
        PlayerCharacter character = active.get(playerId);
        if (character == null) {
            return false;
        }
        character.setExtension(key, json);
        return true;
    }

    public Optional<String> extension(UUID playerId, String key) {
        PlayerCharacter character = active.get(playerId);
        return character == null ? Optional.empty() : character.extension(key);
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
            if (character.isDirty() && !isSaveInFlight(entry.getKey())) {
                submitSave(character.toRecord(now), character.revision(), character);
                queued++;
            }
        }
        for (UnsavedRecord pending : List.copyOf(unsaved.values())) {
            if (!isSaveInFlight(pending.record().playerId())) {
                submitSave(pending.record(), 0L, null);
                queued++;
            }
        }
        return queued;
    }

    private boolean isSaveInFlight(UUID playerId) {
        SaveTracker tracker = saveTrackers.get(playerId);
        return tracker != null && tracker.inFlight();
    }

    /**
     * @param owner the live character the record was taken from, or {@code null} if no live character holds it
     *              (logout, retry of an unsaved record, shutdown)
     */
    private CompletableFuture<Boolean> submitSave(CharacterRecord record, long revision, PlayerCharacter owner) {
        UUID playerId = record.playerId();
        long sequence = nextSaveSequence++;
        saveTrackers.computeIfAbsent(playerId, id -> new SaveTracker()).submitted = sequence;
        CompletableFuture<Boolean> future = io.submit(playerId, () -> {
            boolean ok = saveBlocking(record);
            mainThread.execute(() -> completeSave(record, revision, owner, sequence, ok));
            return ok;
        });
        future.whenComplete((ok, error) -> {
            if (error != null) {
                LOG.error("Save task of {} could not run. UNSAVED STATE: {}", playerId, record, error);
            }
        });
        pendingSaves.add(new PendingSave(record, sequence, future));
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

    private void completeSave(CharacterRecord record, long revision, PlayerCharacter owner, long sequence, boolean ok) {
        UUID playerId = record.playerId();
        SaveTracker tracker = saveTrackers.computeIfAbsent(playerId, id -> new SaveTracker());
        tracker.completed = Math.max(tracker.completed, sequence);
        PlayerCharacter live = active.get(playerId);
        if (ok) {
            if (owner != null && live == owner) {
                owner.markSaved(revision);
            }
            UnsavedRecord pending = unsaved.get(playerId);
            if (pending != null && pending.sequence() <= sequence) {
                // This save was snapshotted after the unsaved one, so it contains its state.
                unsaved.remove(playerId);
            }
        } else if (live == null && sequence == tracker.submitted) {
            // Nobody holds this state any more and no newer save is queued: keep it in memory.
            unsaved.put(playerId, new UnsavedRecord(record, sequence));
        }
        // Otherwise: the live character still holds this state (and stays dirty, so autosave retries it), or a
        // newer save of this player is queued and carries this state plus later changes.
        if (!tracker.inFlight() && live == null && !sessions.contains(playerId) && !unsaved.containsKey(playerId)) {
            saveTrackers.remove(playerId);
        }
    }

    /**
     * Flushes everything and waits for the I/O queue. Called on the server thread after the game loop stopped, so no
     * completion callback runs any more: results are read from the futures directly, failed final saves are retried
     * once synchronously, and whatever still cannot be saved goes to the {@link EmergencyDump}.
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
        for (UnsavedRecord pending : unsaved.values()) {
            submitSave(pending.record(), 0L, null);
        }
        shuttingDown = true;
        active.clear();
        unsaved.clear();
        sessions.clear();
        saveTrackers.clear();

        boolean drained;
        try {
            drained = io.awaitQuiescence(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            drained = false;
        }

        // Only the newest save of each player matters: it contains everything older saves contained.
        Map<UUID, PendingSave> newest = new HashMap<>();
        for (PendingSave pendingSave : pendingSaves) {
            newest.merge(pendingSave.record().playerId(), pendingSave,
                    (a, b) -> a.sequence() >= b.sequence() ? a : b);
        }
        int saved = 0;
        int failed = 0;
        int pending = 0;
        for (PendingSave pendingSave : newest.values()) {
            CompletableFuture<Boolean> future = pendingSave.future();
            if (!future.isDone()) {
                // Still running on the I/O thread: retrying here could race with it.
                pending++;
                LOG.error("Save of {} did not finish before the shutdown timeout", pendingSave.record().playerId());
                emergencyDump.preserve(pendingSave.record());
            } else if (!future.isCompletedExceptionally() && Boolean.TRUE.equals(future.getNow(Boolean.FALSE))) {
                saved++;
            } else if (retrySynchronously(pendingSave.record())) {
                saved++;
            } else {
                failed++;
                emergencyDump.preserve(pendingSave.record());
            }
        }
        ShutdownReport report = new ShutdownReport(newest.size(), saved, failed, pending, drained);
        pendingSaves.clear();
        if (report.clean()) {
            LOG.info("Player data flushed: {}", report);
        } else {
            LOG.error("Player data flush incomplete: {} (see the emergency files / UNSAVED STATE entries above)", report);
        }
        return report;
    }

    private boolean retrySynchronously(CharacterRecord record) {
        try {
            characters.save(record);
            LOG.warn("Saved {} on the final shutdown retry", record.playerId());
            return true;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Final shutdown retry for {} failed", record.playerId(), e);
            return false;
        }
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

    private record PendingSave(CharacterRecord record, long sequence, CompletableFuture<Boolean> future) {
    }

    private record UnsavedRecord(CharacterRecord record, long sequence) {
    }

    /** Sequence numbers of one player's saves (server thread only). */
    private static final class SaveTracker {
        private long submitted;
        private long completed;

        boolean inFlight() {
            return submitted > completed;
        }
    }

    /** Snapshot of the service state for operators ({@code /er admin status}). */
    public record Status(int sessions, long loading, int active, int unsavedInMemory, int savesInFlight) {
    }

    /**
     * Per player whose data was saved during the shutdown window.
     *
     * @param tracked players with a save in the shutdown window
     * @param pending players whose save had not finished when the timeout elapsed (preserved via the emergency dump)
     */
    public record ShutdownReport(int tracked, int saved, int failed, int pending, boolean drained) {
        public boolean clean() {
            return failed == 0 && pending == 0 && drained;
        }
    }
}
