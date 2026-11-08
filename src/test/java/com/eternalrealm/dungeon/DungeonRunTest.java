package com.eternalrealm.dungeon;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** The instance state machine with a small three-room dungeon (time limit 1 hour) unless stated otherwise. */
public class DungeonRunTest {
    private static final UUID ALICE = new UUID(1, 1);
    private static final UUID BOB = new UUID(1, 2);
    private static final UUID STRANGER = new UUID(9, 9);
    private static final long LIMIT = 20L * 60 * 60;
    private static final DungeonDefinition SMALL = new DungeonDefinition("dt", 1, 5, 1, 5, DungeonTheme.FOREST,
            List.of(RoomPlan.of(Wave.of(new MonsterGroup("dt_rat", 2, 1.0))),
                    RoomPlan.of(Wave.of(new MonsterGroup("dt_rat", 1, 0.0)), Wave.of(new MonsterGroup("dt_bat", 1, 0.0))),
                    RoomPlan.of(Wave.of(new MonsterGroup("dt_ogre", 1, 0.0), new MonsterGroup("dt_rat", 1, 0.5)))),
            "bt", LIMIT);

    private static DungeonRun ready(FailurePolicy policy, UUID... members) {
        DungeonRun run = new DungeonRun(SMALL, List.of(members), policy);
        assertEquals(RunState.CREATED, run.state());
        run.startBuilding();
        assertEquals(RunState.BUILDING, run.state());
        run.onBuilt();
        assertEquals(RunState.READY, run.state());
        return run;
    }

    private static DungeonRun started(FailurePolicy policy, long now, UUID... members) {
        DungeonRun run = ready(policy, members);
        run.onStart(now);
        return run;
    }

    private static List<DungeonRunEvent> kill(DungeonRun run, int room, int times) {
        List<DungeonRunEvent> events = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            events.addAll(run.onMonsterKilled(room));
        }
        return events;
    }

    @Test
    public void aFullRunFromCreationToClose() {
        DungeonRun run = ready(FailurePolicy.defaults(), ALICE, BOB);
        List<DungeonRunEvent> start = run.onStart(100);
        assertEquals(List.of(new DungeonRunEvent.Started(100, 100 + LIMIT),
                new DungeonRunEvent.SpawnWave(0, 0, 1, List.of(new MonsterCount("dt_rat", 3)))), start);
        assertEquals(RunState.IN_PROGRESS, run.state());
        assertEquals(0, run.currentRoom());
        assertEquals(0, run.currentWave());
        assertEquals(3, run.remainingInWave());

        assertEquals(List.of(), kill(run, 0, 2));
        assertEquals(List.of(new DungeonRunEvent.OpenDoor(0),
                new DungeonRunEvent.SpawnWave(1, 0, 2, List.of(new MonsterCount("dt_rat", 1)))), kill(run, 0, 1));
        assertEquals(List.of(new DungeonRunEvent.SpawnWave(1, 1, 2, List.of(new MonsterCount("dt_bat", 1)))),
                kill(run, 1, 1));
        assertEquals(1, run.currentWave());
        assertEquals(List.of(new DungeonRunEvent.OpenDoor(1), new DungeonRunEvent.SpawnWave(2, 0, 1,
                List.of(new MonsterCount("dt_ogre", 1), new MonsterCount("dt_rat", 2)))), kill(run, 1, 1));
        assertEquals(List.of(new DungeonRunEvent.OpenDoor(2), new DungeonRunEvent.StartBoss("bt")), kill(run, 2, 3));
        assertEquals(RunState.BOSS, run.state());
        assertEquals(-1, run.currentRoom());

        run.tick(5000);
        assertEquals(List.of(new DungeonRunEvent.Cleared(4950, 100, 5050)), run.onBossDefeated(5050));
        assertEquals(RunState.CLEARED, run.state());
        assertEquals(OptionalLong.of(5050), run.endTick());
        assertEquals(4950, run.elapsedTicks(9999));
        assertEquals(List.of(), run.onBossDefeated(5060));
        assertEquals(List.of(), run.close(6000));
        assertEquals(RunState.CLOSED, run.state());
        assertEquals(List.of(), run.close(6001));
    }

    @Test
    public void wavesAreScaledToTheFixedMembers() {
        DungeonRun solo = new DungeonRun(SMALL, List.of(ALICE), FailurePolicy.defaults());
        DungeonRun five = new DungeonRun(SMALL, List.of(ALICE, BOB, new UUID(1, 3), new UUID(1, 4), new UUID(1, 5)),
                FailurePolicy.defaults());
        assertEquals(List.of(new MonsterCount("dt_rat", 2)), solo.waveMonsters(0, 0));
        assertEquals(List.of(new MonsterCount("dt_rat", 6)), five.waveMonsters(0, 0));
        assertEquals(List.of(new MonsterCount("dt_ogre", 1), new MonsterCount("dt_rat", 3)), five.waveMonsters(2, 0));
        assertEquals(3, five.roomCount());
        assertEquals(2, five.waveCount(1));
        assertEquals(List.of(ALICE, BOB, new UUID(1, 3), new UUID(1, 4), new UUID(1, 5)),
                List.copyOf(five.members()));
        assertThrows(UnsupportedOperationException.class, () -> five.members().add(STRANGER));
    }

    @Test
    public void killsThatDoNotBelongToTheCurrentWaveAreIgnored() {
        DungeonRun run = ready(FailurePolicy.defaults(), ALICE);
        assertEquals("before the start", List.of(), run.onMonsterKilled(0));
        run.onStart(0);
        assertEquals("another room", List.of(), run.onMonsterKilled(1));
        assertEquals(2, run.remainingInWave());
        assertEquals(1, kill(run, 0, 2).stream().filter(e -> e instanceof DungeonRunEvent.OpenDoor).count());
        assertEquals("the cleared room", List.of(), run.onMonsterKilled(0));
        assertEquals(1, run.currentRoom());
        assertEquals(1, run.remainingInWave());
    }

    @Test
    public void theTimeLimitFailsTheRunExactlyAtTheDeadline() {
        DungeonRun run = started(FailurePolicy.defaults(), 1000, ALICE);
        assertEquals(OptionalLong.of(LIMIT), run.remainingTicks(1000));
        assertEquals(List.of(), run.tick(1000 + LIMIT - 1));
        assertEquals(OptionalLong.of(1), run.remainingTicks(1000 + LIMIT - 1));
        assertEquals(List.of(new DungeonRunEvent.Failed(FailureReason.TIME_LIMIT)), run.tick(1000 + LIMIT));
        assertEquals(RunState.FAILED, run.state());
        assertEquals(FailureReason.TIME_LIMIT, run.failureReason().orElseThrow());
        assertEquals("finished runs ignore input", List.of(), run.onMonsterKilled(0));
        assertEquals(List.of(), run.tick(1000 + LIMIT + 5));
        assertEquals(List.of(), run.onPlayerDied(ALICE));
        assertEquals(List.of(), run.close(1000 + LIMIT + 10));
        assertEquals(RunState.CLOSED, run.state());
    }

    @Test
    public void aBossKillAtTheDeadlineDoesNotCount() {
        DungeonRun run = started(FailurePolicy.defaults(), 0, ALICE);
        kill(run, 0, 2);
        kill(run, 1, 2);
        kill(run, 2, 2);
        assertEquals(RunState.BOSS, run.state());
        assertEquals(List.of(new DungeonRunEvent.Failed(FailureReason.TIME_LIMIT)), run.onBossDefeated(LIMIT));
    }

    @Test
    public void theTimeLimitCanBeDisabled() {
        DungeonRun run = started(new FailurePolicy(false, true, 0, 0), 0, ALICE);
        assertEquals(List.of(), run.tick(LIMIT * 10));
        assertEquals(RunState.IN_PROGRESS, run.state());
        assertFalse(run.remainingTicks(5).isPresent());
        DungeonRun other = ready(new FailurePolicy(false, true, 0, 0), ALICE);
        assertEquals(new DungeonRunEvent.Started(7, Long.MAX_VALUE), other.onStart(7).get(0));
    }

    @Test
    public void anEmptyInstanceFailsAfterTheGracePeriod() {
        DungeonRun run = started(new FailurePolicy(true, true, 100, 0), 0, ALICE, BOB);
        run.onPlayerLeft(ALICE);
        run.onPlayerLeft(STRANGER); // not a member: ignored
        assertEquals(List.of(), run.tick(10));
        assertEquals(List.of(BOB), List.copyOf(run.presentMembers()));
        run.onPlayerLeft(BOB);
        assertEquals(List.of(), run.tick(20)); // empty since 20
        assertEquals(List.of(), run.tick(119));
        run.onPlayerReturned(BOB); // reconnect resets the grace period
        assertEquals(List.of(), run.tick(130));
        run.onPlayerLeft(BOB);
        assertEquals(List.of(), run.tick(140));
        assertEquals(List.of(), run.tick(239));
        assertEquals(List.of(new DungeonRunEvent.Failed(FailureReason.EVERYONE_LEFT)), run.tick(240));
    }

    @Test
    public void everyoneLeavingBeforeTheStartAlsoEndsTheInstance() {
        DungeonRun run = ready(new FailurePolicy(true, true, 0, 0), ALICE);
        run.onPlayerLeft(ALICE);
        assertEquals(List.of(new DungeonRunEvent.Failed(FailureReason.EVERYONE_LEFT)), run.tick(50));
        DungeonRun lenient = ready(new FailurePolicy(true, false, 0, 0), ALICE);
        lenient.onPlayerLeft(ALICE);
        assertEquals("rule disabled", List.of(), lenient.tick(50));
    }

    @Test
    public void tooManyDeathsFailTheRunWhenALimitIsSet() {
        DungeonRun limited = ready(new FailurePolicy(true, true, 0, 3), ALICE, BOB);
        assertEquals("deaths before the start do not count", List.of(), limited.onPlayerDied(ALICE));
        limited.onStart(0);
        limited.onPlayerDied(ALICE);
        limited.onPlayerDied(STRANGER);
        limited.onPlayerDied(BOB);
        assertEquals(2, limited.deaths());
        assertEquals(List.of(new DungeonRunEvent.Failed(FailureReason.TOO_MANY_DEATHS)), limited.onPlayerDied(ALICE));

        DungeonRun unlimited = started(FailurePolicy.defaults(), 0, ALICE);
        for (int i = 0; i < 100; i++) {
            assertEquals(List.of(), unlimited.onPlayerDied(ALICE));
        }
        assertEquals(RunState.IN_PROGRESS, unlimited.state());
    }

    @Test
    public void closingAnUnfinishedRunAbortsIt() {
        DungeonRun run = started(FailurePolicy.defaults(), 0, ALICE);
        assertEquals(List.of(new DungeonRunEvent.Failed(FailureReason.ABORTED)), run.close(10));
        assertEquals(RunState.CLOSED, run.state());
        assertEquals(FailureReason.ABORTED, run.failureReason().orElseThrow());
        DungeonRun building = new DungeonRun(SMALL, List.of(ALICE), FailurePolicy.defaults());
        building.startBuilding();
        assertEquals(List.of(new DungeonRunEvent.Failed(FailureReason.ABORTED)), building.close(0));
    }

    @Test
    public void lifecycleCallsInTheWrongStateThrow() {
        DungeonRun run = new DungeonRun(SMALL, List.of(ALICE), FailurePolicy.defaults());
        assertThrows(IllegalStateException.class, run::onBuilt);
        assertThrows(IllegalStateException.class, () -> run.onStart(0));
        run.startBuilding();
        assertThrows(IllegalStateException.class, run::startBuilding);
        run.onBuilt();
        assertThrows(IllegalStateException.class, run::onBuilt);
        run.onStart(50);
        assertThrows(IllegalStateException.class, () -> run.onStart(60));
        assertThrows("time never goes back", IllegalArgumentException.class, () -> run.tick(49));
        assertEquals(OptionalLong.of(50), run.startTick());
        assertEquals(0, new DungeonRun(SMALL, List.of(ALICE), FailurePolicy.defaults()).elapsedTicks(99));
    }

    @Test
    public void thePartyMustFitTheDungeon() {
        assertThrows(IllegalArgumentException.class, () -> new DungeonRun(SMALL, List.of(), FailurePolicy.defaults()));
        List<UUID> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(new UUID(2, i));
        }
        assertThrows(IllegalArgumentException.class, () -> new DungeonRun(SMALL, six, FailurePolicy.defaults()));
        assertThrows(IllegalArgumentException.class, () -> new DungeonRun(SMALL, List.of(ALICE, ALICE),
                FailurePolicy.defaults()));
        assertThrows(NullPointerException.class, () -> new DungeonRun(SMALL, List.of(ALICE), null));
        assertThrows(IllegalArgumentException.class, () -> new FailurePolicy(true, true, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new FailurePolicy(true, true, 0, -1));
    }

    @Test
    public void aRaidGoesStraightToTheBoss() {
        List<UUID> raiders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            raiders.add(new UUID(3, i));
        }
        DungeonRun raid = DungeonRun.raid(Dungeons.FINAL_RAID, raiders, FailurePolicy.defaults());
        raid.startBuilding();
        raid.onBuilt();
        assertEquals(List.of(new DungeonRunEvent.Started(0, Dungeons.FINAL_RAID.timeLimitTicks()),
                new DungeonRunEvent.StartBoss("final")), raid.onStart(0));
        assertEquals(RunState.BOSS, raid.state());
        assertEquals(0, raid.roomCount());
        assertEquals("final", raid.instanceId());
        assertEquals(List.of(new DungeonRunEvent.Cleared(3000, 0, 3000)), raid.onBossDefeated(3000));
        raiders.add(new UUID(3, 99));
        assertThrows("21 raiders", IllegalArgumentException.class,
                () -> DungeonRun.raid(Dungeons.FINAL_RAID, raiders, FailurePolicy.defaults()));
    }

    @Test
    public void everyShippedDungeonCanBeCompleted() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            DungeonRun run = new DungeonRun(dungeon, List.of(ALICE, BOB), FailurePolicy.defaults());
            run.startBuilding();
            run.onBuilt();
            List<DungeonRunEvent> events = new ArrayList<>(run.onStart(0));
            int doors = 0;
            for (int guard = 0; guard < 10_000 && run.state() == RunState.IN_PROGRESS; guard++) {
                for (DungeonRunEvent event : run.onMonsterKilled(run.currentRoom())) {
                    events.add(event);
                    if (event instanceof DungeonRunEvent.OpenDoor) {
                        doors++;
                    }
                }
            }
            assertEquals(dungeon.id(), RunState.BOSS, run.state());
            assertEquals(dungeon.roomCount(), doors);
            assertTrue(events.contains(new DungeonRunEvent.StartBoss(dungeon.bossId())));
            List<DungeonRunEvent> cleared = run.onBossDefeated(20 * 60 * 10);
            assertEquals(List.of(new DungeonRunEvent.Cleared(20 * 60 * 10, 0, 20 * 60 * 10)), cleared);
            assertEquals(RunState.CLEARED, run.state());
        }
    }
}
