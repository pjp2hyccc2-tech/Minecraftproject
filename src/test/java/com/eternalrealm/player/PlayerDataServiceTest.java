package com.eternalrealm.player;

import com.eternalrealm.character.AllocationResult;
import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.ExperienceResult;
import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.character.PowerExperienceCurve;
import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatType;
import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.persistence.file.AtomicFileStore;
import com.eternalrealm.persistence.file.FileAccountRepository;
import com.eternalrealm.persistence.file.FileCharacterRepository;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.testutil.ManualExecutor;
import com.eternalrealm.testutil.MutableClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests of the player data lifecycle: real asynchronous I/O executor, server thread simulated by
 * {@link ManualExecutor}.
 */
public class PlayerDataServiceTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static final SeasonId SEASON = SeasonId.of("s1");
    private static final Duration AUTOSAVE = Duration.ofSeconds(60);
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000b0b");

    private final MutableClock clock = MutableClock.startingAt("2026-11-08T00:00:00Z");
    private final ManualExecutor main = new ManualExecutor();
    private final RecordingListener listener = new RecordingListener();
    private final List<CharacterRecord> emergency = new ArrayList<>();
    private KeyedSerialExecutor<UUID> io;
    private InMemoryRepositories.Characters characters;
    private InMemoryRepositories.Accounts accounts;
    private PlayerDataService service;

    @Before
    public void setUp() {
        io = new KeyedSerialExecutor<>(2, "test-io");
        characters = new InMemoryRepositories.Characters();
        accounts = new InMemoryRepositories.Accounts();
        service = newService(accounts, characters);
    }

    @After
    public void tearDown() {
        io.shutdown(Duration.ofSeconds(5));
    }

    private PlayerDataService newService(AccountRepository accountRepo, com.eternalrealm.character.CharacterRepository characterRepo) {
        return new PlayerDataService(accountRepo, characterRepo, io, main, clock, SEASON, CharacterRules.defaults(),
                AUTOSAVE, listener, emergency::add);
    }

    private CharacterRecord stored(UUID player) {
        return characters.stored.get(InMemoryRepositories.Characters.key(player, SEASON));
    }

    /** Lets all I/O finish and runs every resulting server-thread callback. */
    private void settle() throws InterruptedException {
        for (int round = 0; round < 20; round++) {
            assertTrue("I/O did not finish", io.awaitQuiescence(Duration.ofSeconds(10)));
            if (main.runAll() == 0 && io.pendingKeys() == 0) {
                return;
            }
        }
        throw new AssertionError("did not settle");
    }

    private void joinAndLoad(UUID player, String name) throws InterruptedException {
        service.onPlayerJoin(player, name);
        settle();
    }

    @Test
    public void firstJoinCreatesLevelOneCharacterAndAccount() throws Exception {
        joinAndLoad(ALICE, "Alice");

        assertTrue(service.isActive(ALICE));
        assertEquals(Optional.of(SessionState.ACTIVE), service.sessionState(ALICE));
        assertEquals(1, service.snapshot(ALICE).orElseThrow().level());
        assertEquals(List.of(ALICE), listener.ready);
        AccountRecord account = accounts.stored.get(ALICE);
        assertEquals("Alice", account.lastKnownName());
        assertEquals(1, account.loginCount());
    }

    @Test
    public void existingCharacterIsLoaded() throws Exception {
        Instant t = clock.instant();
        characters.stored.put(InMemoryRepositories.Characters.key(ALICE, SEASON),
                new CharacterRecord(ALICE, SEASON, 7, 10, 999, StatBlock.ZERO.with(StatType.STR, 3), t, t));

        joinAndLoad(ALICE, "Alice");

        CharacterSnapshot snapshot = service.snapshot(ALICE).orElseThrow();
        assertEquals(7, snapshot.level());
        assertEquals(10, snapshot.experience());
        assertEquals(8, snapshot.totalStats().get(StatType.STR));
    }

    @Test
    public void loadFailureRejectsThePlayerAndNeverCreatesOrSavesData() throws Exception {
        characters.failLoads = true;
        joinAndLoad(ALICE, "Alice");

        assertFalse(service.isActive(ALICE));
        assertEquals(Optional.of(SessionState.FAILED), service.sessionState(ALICE));
        assertEquals(1, listener.loadFailures.size());
        assertEquals(AllocationResult.NOT_READY, service.allocateStat(ALICE, StatType.STR, 1));
        assertEquals(Optional.empty(), service.grantExperience(ALICE, 100, ExperienceSource.ADMIN));

        service.onPlayerLeave(ALICE);
        settle();
        assertEquals(0, characters.saves.get());
        assertTrue(characters.stored.isEmpty());
    }

    @Test
    public void leavingSavesTheCharacter() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 500, ExperienceSource.ADMIN);
        int level = service.snapshot(ALICE).orElseThrow().level();

        service.onPlayerLeave(ALICE);
        settle();

        CharacterRecord saved = characters.stored.get(InMemoryRepositories.Characters.key(ALICE, SEASON));
        assertEquals(level, saved.level());
        assertEquals(500, saved.totalExperience());
        assertFalse(service.sessionState(ALICE).isPresent());
    }

    @Test
    public void leavingWhileLoadingDiscardsTheLateResult() throws Exception {
        characters.loadGate = new CountDownLatch(1);
        service.onPlayerJoin(ALICE, "Alice");
        service.onPlayerLeave(ALICE);
        characters.loadGate.countDown();
        settle();

        assertFalse(service.isActive(ALICE));
        assertTrue(listener.ready.isEmpty());
        assertEquals("nothing was loaded, so nothing may be saved", 0, characters.saves.get());
    }

    @Test
    public void rejoinWhileTheOldLoadIsPendingAppliesOnlyTheNewSessionsResult() throws Exception {
        characters.loadGate = new CountDownLatch(1);
        service.onPlayerJoin(ALICE, "Alice");   // generation 1, load blocked
        service.onPlayerLeave(ALICE);
        service.onPlayerJoin(ALICE, "Alice");   // generation 2, queued behind generation 1
        characters.loadGate.countDown();
        settle();

        assertTrue(service.isActive(ALICE));
        assertEquals("the stale generation-1 result must be discarded", List.of(ALICE), listener.ready);
        assertEquals(2, accounts.stored.get(ALICE).loginCount());
    }

    @Test
    public void quickReconnectLoadsTheDataSavedOnLogout() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 2_000, ExperienceSource.ADMIN);
        int level = service.snapshot(ALICE).orElseThrow().level();
        assertTrue(level > 1);

        characters.saveGate = new CountDownLatch(1); // the logout save is slow...
        service.onPlayerLeave(ALICE);
        service.onPlayerJoin(ALICE, "Alice");        // ...and the player reconnects immediately
        characters.saveGate.countDown();
        settle();

        assertEquals("load must run after the pending save", level, service.snapshot(ALICE).orElseThrow().level());
    }

    @Test
    public void failedLogoutSaveIsKeptInMemoryAndWinsOverStaleDiskData() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.onPlayerLeave(ALICE);
        settle(); // level 1 is now on "disk"

        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 2_000, ExperienceSource.ADMIN);
        int level = service.snapshot(ALICE).orElseThrow().level();
        characters.failNextSaves.set(PlayerDataService.SAVE_ATTEMPTS);
        service.onPlayerLeave(ALICE);
        settle();
        assertEquals(1, service.status().unsavedInMemory());

        joinAndLoad(ALICE, "Alice");
        assertEquals("in-memory data must win over the stale level 1 on disk", level,
                service.snapshot(ALICE).orElseThrow().level());
        assertEquals(0, service.status().unsavedInMemory());

        assertEquals(1, service.saveAllDirty());
        settle();
        assertEquals(level, characters.stored.get(InMemoryRepositories.Characters.key(ALICE, SEASON)).level());
    }

    @Test
    public void autosaveRetriesUnsavedRecordsOfOfflinePlayers() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 700, ExperienceSource.ADMIN);
        characters.failNextSaves.set(PlayerDataService.SAVE_ATTEMPTS);
        service.onPlayerLeave(ALICE);
        settle();
        assertEquals(1, service.status().unsavedInMemory());

        clock.advance(AUTOSAVE);
        service.tick();
        settle();
        assertEquals(0, service.status().unsavedInMemory());
        assertEquals(700, characters.stored.get(InMemoryRepositories.Characters.key(ALICE, SEASON)).totalExperience());
    }

    @Test
    public void autosaveWritesOnlyDirtyCharacters() throws Exception {
        joinAndLoad(ALICE, "Alice");
        joinAndLoad(BOB, "Bob");

        clock.advance(AUTOSAVE);
        service.tick();
        settle();
        assertEquals("both new characters are saved once", 2, characters.saves.get());

        service.grantExperience(BOB, 10, ExperienceSource.ADMIN);
        clock.advance(AUTOSAVE);
        service.tick();
        settle();
        assertEquals("only Bob changed", 3, characters.saves.get());

        clock.advance(AUTOSAVE);
        service.tick();
        settle();
        assertEquals("nothing changed", 3, characters.saves.get());
    }

    @Test
    public void levelUpIsReportedToTheListener() throws Exception {
        joinAndLoad(ALICE, "Alice");
        ExperienceResult result = service.grantExperience(ALICE, 100, ExperienceSource.MOB_KILL).orElseThrow();
        assertTrue(result.leveledUp());
        assertEquals(1, listener.levelUps.size());
        assertTrue(listener.changed.contains(ALICE));
    }

    @Test
    public void statAllocationGoesThroughServerValidation() throws Exception {
        assertEquals(AllocationResult.NOT_READY, service.allocateStat(ALICE, StatType.STR, 1));
        joinAndLoad(ALICE, "Alice");
        assertEquals(AllocationResult.NOT_ENOUGH_POINTS, service.allocateStat(ALICE, StatType.STR, 1));
        service.grantExperience(ALICE, 100, ExperienceSource.ADMIN);
        assertEquals(AllocationResult.INVALID_AMOUNT, service.allocateStat(ALICE, StatType.STR, 1000));
        assertEquals(AllocationResult.SUCCESS, service.allocateStat(ALICE, StatType.STR, 3));
        assertEquals(0, service.snapshot(ALICE).orElseThrow().unspentStatPoints());
        assertTrue(service.resetStatAllocation(ALICE));
        assertEquals(3, service.snapshot(ALICE).orElseThrow().unspentStatPoints());
        assertEquals(SetLevelResult.SUCCESS, service.setLevel(ALICE, 20));
        assertEquals(57, service.snapshot(ALICE).orElseThrow().unspentStatPoints());
    }

    @Test
    public void reloadedRulesAreAppliedToOnlineCharacters() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.setLevel(ALICE, 10);
        service.allocateStat(ALICE, StatType.VIT, 10);
        listener.changed.clear();

        CharacterRules noPoints = new CharacterRules(50, 0, 5, 10, new PowerExperienceCurve(100, 1.5));
        service.updateRules(noPoints);

        CharacterSnapshot snapshot = service.snapshot(ALICE).orElseThrow();
        assertEquals(0, snapshot.allocatedStats().total());
        assertEquals(0, snapshot.unspentStatPoints());
        assertTrue("clients must be re-synced", listener.changed.contains(ALICE));
    }

    @Test
    public void shutdownFlushesEverythingAndReportsIt() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 250, ExperienceSource.ADMIN);

        PlayerDataService.ShutdownReport report = service.shutdown(Duration.ofSeconds(10));

        assertTrue(report.toString(), report.clean());
        assertEquals(1, report.saved());
        assertEquals(250, characters.stored.get(InMemoryRepositories.Characters.key(ALICE, SEASON)).totalExperience());
        service.onPlayerJoin(BOB, "Bob"); // ignored after shutdown
        assertFalse(service.sessionState(BOB).isPresent());
    }

    /**
     * Regression test for the independent review finding: an autosave that fails while a newer logout save is queued
     * must not be kept and later written over the newer data (progress rollback).
     */
    @Test
    public void olderFailedSaveIsNotResurrectedAfterANewerSaveSucceeded() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 100, ExperienceSource.ADMIN);
        characters.failNextSaves.set(PlayerDataService.SAVE_ATTEMPTS); // autosave A will fail every attempt
        clock.advance(AUTOSAVE);
        service.tick();                                                   // A: totalExperience 100
        service.grantExperience(ALICE, 5_000, ExperienceSource.ADMIN);
        service.onPlayerLeave(ALICE);                                     // B: totalExperience 5100, queued behind A
        settle();

        assertEquals(5_100, stored(ALICE).totalExperience());
        assertEquals("the stale failed save must not be kept", 0, service.status().unsavedInMemory());

        clock.advance(AUTOSAVE);
        service.tick();
        settle();
        assertEquals("nothing may overwrite the newer data", 5_100, stored(ALICE).totalExperience());

        joinAndLoad(ALICE, "Alice");
        // 5100 EXP from level 1 = level 7 (100+283+520+800+1118+1470 = 4291, remainder 809); 100 EXP would be level 2.
        CharacterSnapshot reloaded = service.snapshot(ALICE).orElseThrow();
        assertEquals(7, reloaded.level());
        assertEquals(809, reloaded.experience());
    }

    /**
     * The window between the older failure's callback and the newer save's completion: a shutdown there must not
     * re-save the stale failed state after the newer one.
     */
    @Test
    public void shutdownNeverReplaysAStaleFailureWhileANewerSaveIsQueued() throws Exception {
        // Configure the gate before any save can run: only B (totalExperience 5100) waits at it.
        characters.saveGateFilter = record -> record.totalExperience() == 5_100;
        characters.saveGate = new CountDownLatch(1);
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 100, ExperienceSource.ADMIN);
        characters.failNextSaves.set(PlayerDataService.SAVE_ATTEMPTS);
        clock.advance(AUTOSAVE);
        service.tick();                                                    // A: 100, fails
        service.grantExperience(ALICE, 5_000, ExperienceSource.ADMIN);
        service.onPlayerLeave(ALICE);                                      // B: 5100, held at the gate

        long deadline = System.nanoTime() + 10_000_000_000L;
        while (main.isEmpty()) {                                           // wait for A's failure callback
            assertTrue("A did not complete", System.nanoTime() < deadline);
            Thread.sleep(10);
        }
        main.runAll();                                                     // run it while B is still queued

        Thread releaser = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            characters.saveGate.countDown();
        });
        releaser.start();
        PlayerDataService.ShutdownReport report = service.shutdown(Duration.ofSeconds(10));
        releaser.join();

        assertTrue(report.toString(), report.clean());
        assertEquals(5_100, stored(ALICE).totalExperience());
    }

    @Test
    public void newestFailedSaveIsKeptWhenEveryQueuedSaveFails() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 100, ExperienceSource.ADMIN);
        characters.failNextSaves.set(2 * PlayerDataService.SAVE_ATTEMPTS); // both A and B fail
        clock.advance(AUTOSAVE);
        service.tick();                                                     // A: 100
        service.grantExperience(ALICE, 5_000, ExperienceSource.ADMIN);
        service.onPlayerLeave(ALICE);                                       // B: 5100
        settle();
        assertEquals(1, service.status().unsavedInMemory());

        clock.advance(AUTOSAVE);
        service.tick();
        settle();
        assertEquals("the newest state (B) must be the one retried", 5_100, stored(ALICE).totalExperience());
        assertEquals(0, service.status().unsavedInMemory());
    }

    @Test
    public void failedFinalSaveIsRetriedOnceAtShutdown() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 300, ExperienceSource.ADMIN);
        characters.failNextSaves.set(PlayerDataService.SAVE_ATTEMPTS); // the I/O attempts fail, the final retry works
        PlayerDataService.ShutdownReport report = service.shutdown(Duration.ofSeconds(10));
        assertTrue(report.toString(), report.clean());
        assertEquals(300, stored(ALICE).totalExperience());
        assertTrue(emergency.isEmpty());
    }

    @Test
    public void dataThatCannotBeSavedAtShutdownGoesToTheEmergencyDump() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.grantExperience(ALICE, 300, ExperienceSource.ADMIN);
        characters.failNextSaves.set(PlayerDataService.SAVE_ATTEMPTS + 1);
        PlayerDataService.ShutdownReport report = service.shutdown(Duration.ofSeconds(10));
        assertFalse(report.clean());
        assertEquals(1, report.failed());
        assertEquals(1, emergency.size());
        assertEquals(300, emergency.get(0).totalExperience());
    }

    @Test
    public void progressSurvivesAServerRestartWithFileStorage() throws Exception {
        Path dataRoot = tmp.newFolder("world", "eternalrealm").toPath();
        AtomicFileStore store = new AtomicFileStore(dataRoot.resolve("quarantine"), clock);
        PlayerDataService first = newService(new FileAccountRepository(dataRoot, store), new FileCharacterRepository(dataRoot, store));
        first.onPlayerJoin(ALICE, "Alice");
        settle();
        first.grantExperience(ALICE, 1_000, ExperienceSource.ADMIN);
        first.allocateStat(ALICE, StatType.INT, 2);
        CharacterSnapshot before = first.snapshot(ALICE).orElseThrow();
        first.onPlayerLeave(ALICE);
        assertTrue(first.shutdown(Duration.ofSeconds(10)).clean());

        // "Restart": a brand new service instance reading the same directory.
        PlayerDataService second = newService(new FileAccountRepository(dataRoot, store), new FileCharacterRepository(dataRoot, store));
        second.onPlayerJoin(ALICE, "Alice");
        settle();
        assertEquals(before, second.snapshot(ALICE).orElseThrow());
    }

    /** Records listener callbacks (always invoked on the simulated server thread). */
    private static final class RecordingListener implements PlayerDataListener {
        final List<UUID> ready = new ArrayList<>();
        final List<UUID> changed = new ArrayList<>();
        final List<ExperienceResult> levelUps = new ArrayList<>();
        final List<String> loadFailures = new ArrayList<>();

        @Override
        public void onCharacterReady(UUID playerId) {
            ready.add(playerId);
        }

        @Override
        public void onCharacterChanged(UUID playerId) {
            changed.add(playerId);
        }

        @Override
        public void onLevelUp(UUID playerId, ExperienceResult result) {
            levelUps.add(result);
        }

        @Override
        public void onLoadFailed(UUID playerId, String incidentId) {
            loadFailures.add(incidentId);
        }
    }

    // ------------------------------------------------------------------ Phase 3

    @Test
    public void firstJobChoiceIsFreeAndChangesCostGoldAboveTheThreshold() throws Exception {
        joinAndLoad(ALICE, "Alice");
        com.eternalrealm.job.JobRules rules = com.eternalrealm.job.JobRules.defaults();
        PlayerDataService.JobChange first = service.chooseJob(ALICE, com.eternalrealm.job.JobType.MAGE, rules);
        assertEquals(PlayerDataService.JobChangeResult.OK, first.result());
        assertTrue(first.firstChoice());
        assertEquals(PlayerDataService.JobChangeResult.SAME_JOB,
                service.chooseJob(ALICE, com.eternalrealm.job.JobType.MAGE, rules).result());
        service.setLevel(ALICE, 20);
        PlayerDataService.JobChange poor = service.chooseJob(ALICE, com.eternalrealm.job.JobType.KNIGHT, rules);
        assertEquals(PlayerDataService.JobChangeResult.NOT_ENOUGH_GOLD, poor.result());
        assertEquals(2000L, poor.cost());
        service.grantGold(ALICE, 2500);
        PlayerDataService.JobChange paid = service.chooseJob(ALICE, com.eternalrealm.job.JobType.KNIGHT, rules);
        assertEquals(PlayerDataService.JobChangeResult.OK, paid.result());
        assertFalse(paid.firstChoice());
        assertEquals(500L, service.gold(ALICE));
        assertEquals("knight", service.snapshot(ALICE).orElseThrow().jobId());
        assertEquals(PlayerDataService.JobChangeResult.NOT_READY,
                service.chooseJob(BOB, com.eternalrealm.job.JobType.KNIGHT, rules).result());
    }

    /**
     * The second class (二次職): from level 40, free the first time; the other class costs gold and refunds the old
     * class's points while the job's stay; the class is stored as the job id and survives a reload.
     */
    @Test
    public void theJobGrowsIntoASecondClassAtLevelForty() throws Exception {
        joinAndLoad(ALICE, "Alice");
        com.eternalrealm.job.JobRules rules = com.eternalrealm.job.JobRules.defaults();
        assertEquals(PlayerDataService.ClassChangeResult.NO_JOB,
                service.advanceClass(ALICE, com.eternalrealm.job.JobClass.GUARDIAN, rules).result());
        service.chooseJob(ALICE, com.eternalrealm.job.JobType.KNIGHT, rules);
        service.setLevel(ALICE, 39);
        assertEquals(PlayerDataService.ClassChangeResult.TOO_LOW,
                service.advanceClass(ALICE, com.eternalrealm.job.JobClass.GUARDIAN, rules).result());
        service.setLevel(ALICE, 40);
        assertEquals(PlayerDataService.ClassChangeResult.WRONG_JOB,
                service.advanceClass(ALICE, com.eternalrealm.job.JobClass.SAGE, rules).result());
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.WRONG_JOB,
                service.investSkill(ALICE, "guardian_titan_stance").orElseThrow());
        service.investSkill(ALICE, "knight_provoke");

        PlayerDataService.ClassChange first = service.advanceClass(ALICE, com.eternalrealm.job.JobClass.GUARDIAN, rules);
        assertEquals(PlayerDataService.ClassChangeResult.OK, first.result());
        assertTrue(first.first());
        assertEquals("the first advance is free", 0L, first.cost());
        assertEquals("guardian", service.snapshot(ALICE).orElseThrow().jobId());
        assertEquals(PlayerDataService.ClassChangeResult.SAME_CLASS,
                service.advanceClass(ALICE, com.eternalrealm.job.JobClass.GUARDIAN, rules).result());
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.OK,
                service.investSkill(ALICE, "guardian_titan_stance").orElseThrow());
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.WRONG_JOB,
                service.investSkill(ALICE, "paladin_radiant_edge").orElseThrow());
        int unspent = service.snapshot(ALICE).orElseThrow().unspentSkillPoints();

        PlayerDataService.ClassChange poor = service.advanceClass(ALICE, com.eternalrealm.job.JobClass.PALADIN, rules);
        assertEquals(PlayerDataService.ClassChangeResult.NOT_ENOUGH_GOLD, poor.result());
        assertEquals(rules.classChangeCost(40), poor.cost());
        service.grantGold(ALICE, rules.classChangeCost(40));
        PlayerDataService.ClassChange changed = service.advanceClass(ALICE, com.eternalrealm.job.JobClass.PALADIN, rules);
        assertEquals(PlayerDataService.ClassChangeResult.OK, changed.result());
        assertFalse(changed.first());
        assertEquals(0L, service.gold(ALICE));
        CharacterSnapshot paladin = service.snapshot(ALICE).orElseThrow();
        assertEquals("paladin", paladin.jobId());
        assertEquals("the job's own points stay", 1, paladin.investment("knight_provoke"));
        assertEquals("the old class's points come back", 0, paladin.investment("guardian_titan_stance"));
        assertEquals(unspent + 1, paladin.unspentSkillPoints());
        assertFalse(paladin.skillLoadout().contains("guardian_titan_stance"));

        service.onPlayerLeave(ALICE);
        settle();
        assertEquals("paladin", stored(ALICE).jobId());
        joinAndLoad(ALICE, "Alice");
        assertEquals("paladin", service.snapshot(ALICE).orElseThrow().jobId());
        assertEquals(1, service.snapshot(ALICE).orElseThrow().investment("knight_provoke"));
        // a new job leaves the class behind
        service.grantGold(ALICE, 1_000_000L);
        service.chooseJob(ALICE, com.eternalrealm.job.JobType.MAGE, rules);
        assertEquals("mage", service.snapshot(ALICE).orElseThrow().jobId());
        assertEquals(PlayerDataService.JobChangeResult.SAME_JOB,
                service.chooseJob(ALICE, com.eternalrealm.job.JobType.MAGE, rules).result());
    }

    @Test
    public void skillInvestmentIsValidatedAndPersisted() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.chooseJob(ALICE, com.eternalrealm.job.JobType.PRIEST, com.eternalrealm.job.JobRules.defaults());
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.NO_POINTS,
                service.investSkill(ALICE, "priest_heal").orElseThrow());
        service.setLevel(ALICE, 10); // 9 * 3 + 3 = 30 points
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.OK, service.investSkill(ALICE, "priest_heal").orElseThrow());
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.WRONG_JOB,
                service.investSkill(ALICE, "mage_fireball").orElseThrow());
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.LOCKED,
                service.investSkill(ALICE, "priest_blessing").orElseThrow());
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.PREREQUISITE,
                service.investSkill(ALICE, "priest_greater_heal").orElseThrow());
        assertEquals(1, service.snapshot(ALICE).orElseThrow().investment("priest_heal"));
        assertEquals(29, service.snapshot(ALICE).orElseThrow().unspentSkillPoints());
        service.onPlayerLeave(ALICE);
        settle();
        assertEquals(1, (int) stored(ALICE).skillInvestments().get("priest_heal"));
        assertEquals("priest", stored(ALICE).jobId());
        assertTrue(service.investSkill(ALICE, "priest_heal").isEmpty());
    }

    @Test
    public void learnedActivesFillFreeSlotsAndTheLoadoutIsValidatedAndPersisted() throws Exception {
        joinAndLoad(ALICE, "Alice");
        service.chooseJob(ALICE, com.eternalrealm.job.JobType.MAGE, com.eternalrealm.job.JobRules.defaults());
        service.setLevel(ALICE, 20);
        for (int i = 0; i < 4; i++) {
            service.investSkill(ALICE, "mage_fireball");
        }
        assertEquals(com.eternalrealm.job.SkillTree.InvestResult.OK, service.investSkill(ALICE, "mage_explosion")
                .orElseThrow());
        // the newly learned active went into the first free slot (5)
        assertEquals(java.util.List.of("mage_fireball", "mage_ice_lance", "mage_thunderstorm", "mage_meteor",
                "mage_explosion", ""), service.snapshot(ALICE).orElseThrow().skillLoadout());
        assertEquals(com.eternalrealm.job.SkillTree.LoadoutResult.OK,
                service.setSkillSlot(ALICE, 1, "mage_explosion").orElseThrow());
        assertEquals(java.util.List.of("mage_explosion", "mage_ice_lance", "mage_thunderstorm", "mage_meteor",
                "mage_fireball", ""), service.snapshot(ALICE).orElseThrow().skillLoadout());
        assertEquals(com.eternalrealm.job.SkillTree.LoadoutResult.NOT_LEARNED,
                service.setSkillSlot(ALICE, 6, "mage_frost_bolt").orElseThrow());
        assertEquals(com.eternalrealm.job.SkillTree.LoadoutResult.NEEDS_ULTIMATE,
                service.setSkillSlot(ALICE, 4, "mage_fireball").orElseThrow());
        service.onPlayerLeave(ALICE);
        settle();
        assertEquals("mage_explosion", stored(ALICE).skillLoadout().get(0));
        assertTrue(service.setSkillSlot(ALICE, 1, "").isEmpty());
    }

    @Test
    public void theSkillResetCostsGoldAndRefundsEverything() throws Exception {
        joinAndLoad(ALICE, "Alice");
        com.eternalrealm.job.JobRules rules = com.eternalrealm.job.JobRules.defaults();
        service.chooseJob(ALICE, com.eternalrealm.job.JobType.PRIEST, rules);
        assertEquals(PlayerDataService.SkillResetResult.NOTHING_TO_RESET, service.resetSkills(ALICE, rules).result());
        service.setLevel(ALICE, 20);
        service.investSkill(ALICE, "priest_heal");
        service.investSkill(ALICE, "priest_renew");
        PlayerDataService.SkillReset poor = service.resetSkills(ALICE, rules);
        assertEquals(PlayerDataService.SkillResetResult.NOT_ENOUGH_GOLD, poor.result());
        assertEquals(1000L, poor.cost());
        service.grantGold(ALICE, 1500L);
        assertEquals(PlayerDataService.SkillResetResult.OK, service.resetSkills(ALICE, rules).result());
        assertEquals(500L, service.gold(ALICE));
        assertTrue(service.snapshot(ALICE).orElseThrow().skillInvestments().isEmpty());
        assertTrue(service.snapshot(ALICE).orElseThrow().skillLoadout().isEmpty());
        assertEquals(PlayerDataService.SkillResetResult.NOT_READY, service.resetSkills(BOB, rules).result());
    }

    @Test
    public void theTestKitMaxesTheCharacterOutWithEveryPointUnspent() throws Exception {
        assertTrue("nothing to max out before the data is loaded", service.maxOut(ALICE).isEmpty());
        joinAndLoad(ALICE, "Alice");
        service.chooseJob(ALICE, com.eternalrealm.job.JobType.PRIEST, com.eternalrealm.job.JobRules.defaults());
        service.setLevel(ALICE, 20);
        assertEquals(AllocationResult.SUCCESS, service.allocateStat(ALICE, StatType.VIT, 10));
        service.investSkill(ALICE, "priest_heal");
        service.grantGold(ALICE, 1234L);
        long earnedBefore = service.totalExperience(ALICE);
        listener.changed.clear();

        CharacterSnapshot maxed = service.maxOut(ALICE).orElseThrow();
        assertEquals(CharacterRules.MAX_LEVEL, maxed.level());
        assertTrue(maxed.atCap());
        assertEquals(0L, maxed.experienceToNext());
        assertEquals(CharacterRecord.MAX_GOLD, maxed.gold());
        assertEquals("every stat point is back in the pool", 0, maxed.allocatedStats().total());
        assertEquals("99 levels x 3 points", 297, maxed.unspentStatPoints());
        assertEquals("99 x 3 + 10 milestones x 3", 327, maxed.unspentSkillPoints());
        assertTrue(maxed.skillInvestments().isEmpty());
        assertTrue(maxed.skillLoadout().isEmpty());
        assertEquals("the job stays", "priest", maxed.jobId());
        assertEquals("no EXP counts as earned", earnedBefore, service.totalExperience(ALICE));
        assertTrue("the client is re-synced", listener.changed.contains(ALICE));
        assertEquals("doing it again changes nothing", maxed, service.maxOut(ALICE).orElseThrow());

        // a server with a lower cap: the cap of its rules
        service.updateRules(new CharacterRules(50, 3, 5, 10, new PowerExperienceCurve(100, 1.5)));
        CharacterSnapshot capped = service.maxOut(ALICE).orElseThrow();
        assertEquals(50, capped.level());
        assertEquals(147, capped.unspentStatPoints());
        assertEquals(49 * 3 + 5 * 3, capped.unspentSkillPoints());

        service.onPlayerLeave(ALICE);
        settle();
        assertEquals(50, stored(ALICE).level());
        assertEquals(CharacterRecord.MAX_GOLD, stored(ALICE).gold());
        assertTrue(stored(ALICE).skillInvestments().isEmpty());
    }

    @Test
    public void skillsOfAnotherJobAreRefundedOnLoad() throws Exception {
        java.time.Instant t = clock.instant();
        characters.stored.put(InMemoryRepositories.Characters.key(ALICE, SEASON), new CharacterRecord(ALICE, SEASON, 20, 0, 999, com.eternalrealm.character.StatBlock.ZERO, t, t,
                "mage", java.util.Map.of("priest_heal", 2), 50L, java.util.Map.of()));
        joinAndLoad(ALICE, "Alice");
        assertTrue(service.snapshot(ALICE).orElseThrow().skillInvestments().isEmpty());
        assertEquals(50L, service.gold(ALICE));
    }

    @Test
    public void goldDeathPenaltyAndExtensions() throws Exception {
        joinAndLoad(ALICE, "Alice");
        assertEquals(Long.valueOf(300L), service.grantGold(ALICE, 300).orElseThrow());
        assertFalse(service.spendGold(ALICE, 301));
        assertTrue(service.spendGold(ALICE, 100));
        assertEquals(200L, service.gold(ALICE));
        service.grantExperience(ALICE, 50, com.eternalrealm.character.ExperienceSource.ADMIN);
        assertEquals(5L, service.applyDeathPenalty(ALICE, 0.05));
        assertEquals(45L, service.snapshot(ALICE).orElseThrow().experience());
        assertTrue(service.setExtension(ALICE, "quests", "{\"q\":1}"));
        assertEquals("{\"q\":1}", service.extension(ALICE, "quests").orElseThrow());
        assertFalse(service.setExtension(BOB, "quests", "{}"));
        assertTrue(service.grantGold(BOB, 5).isEmpty());
        service.onPlayerLeave(ALICE);
        settle();
        assertEquals("{\"q\":1}", stored(ALICE).extensions().get("quests"));
        assertEquals(200L, stored(ALICE).gold());
    }
}
