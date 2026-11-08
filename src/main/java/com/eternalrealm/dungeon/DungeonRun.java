package com.eternalrealm.dungeon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/**
 * State machine of one dungeon or raid instance (docs/modules/boss.md). Server thread only; time is the server tick.
 *
 * <p>The members are fixed when the run is created: nobody joins later, and wave sizes are scaled to their number.
 * Every input returns the {@link DungeonRunEvent}s the platform must carry out, in order. Inputs that arrive in a
 * state where they mean nothing (a kill after the run failed, a second boss death) are ignored and return no event;
 * lifecycle calls in the wrong state ({@link #onBuilt()} twice) are programming errors and throw.</p>
 */
public final class DungeonRun {
    private static final long NONE = Long.MIN_VALUE;

    private final String instanceId;
    private final String bossId;
    private final List<List<List<MonsterCount>>> waves;
    private final long timeLimitTicks;
    private final FailurePolicy policy;
    private final Set<UUID> members;
    private final Set<UUID> absent = new HashSet<>();

    private RunState state = RunState.CREATED;
    private int room = -1;
    private int wave = -1;
    private int remainingInWave;
    private int deaths;
    private long lastTick = NONE;
    private long startTick = NONE;
    private long endTick = NONE;
    private long emptySince = NONE;
    private FailureReason failure;

    /** A party dungeon run; {@code members} must be a valid party size for the definition. */
    public DungeonRun(DungeonDefinition definition, Collection<UUID> members, FailurePolicy policy) {
        this(definition.id(), definition.bossId(), definition.rooms(), definition.timeLimitTicks(),
                definition.minPartySize(), definition.maxPartySize(), members, policy);
    }

    private DungeonRun(String instanceId, String bossId, List<RoomPlan> rooms, long timeLimitTicks, int minParty,
                       int maxParty, Collection<UUID> members, FailurePolicy policy) {
        this.instanceId = instanceId;
        this.bossId = bossId;
        this.timeLimitTicks = timeLimitTicks;
        this.policy = Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(members, "members");
        Set<UUID> unique = new LinkedHashSet<>();
        for (UUID member : members) {
            if (!unique.add(Objects.requireNonNull(member, "member"))) {
                throw new IllegalArgumentException("member listed twice: " + member);
            }
        }
        Check.range(instanceId + ": party size", unique.size(), minParty, maxParty);
        this.members = Collections.unmodifiableSet(unique);
        List<List<List<MonsterCount>>> scaled = new ArrayList<>();
        for (RoomPlan plan : rooms) {
            List<List<MonsterCount>> roomWaves = new ArrayList<>();
            for (Wave roomWave : plan.waves()) {
                roomWaves.add(roomWave.spawnsFor(unique.size()));
            }
            scaled.add(List.copyOf(roomWaves));
        }
        this.waves = List.copyOf(scaled);
    }

    /** A raid run: no rooms, the boss fight starts with the run. */
    public static DungeonRun raid(RaidDefinition raid, Collection<UUID> members, FailurePolicy policy) {
        return new DungeonRun(raid.id(), raid.bossId(), List.of(), raid.timeLimitTicks(), raid.minPartySize(),
                raid.maxPartySize(), members, policy);
    }

    // ------------------------------------------------------------------ lifecycle

    /** CREATED -> BUILDING: the platform starts placing the layout. */
    public List<DungeonRunEvent> startBuilding() {
        require(RunState.CREATED, "startBuilding");
        state = RunState.BUILDING;
        return List.of();
    }

    /** BUILDING -> READY: every block is placed; members may be brought into the entrance hall. */
    public List<DungeonRunEvent> onBuilt() {
        require(RunState.BUILDING, "onBuilt");
        state = RunState.READY;
        return List.of();
    }

    /** READY -> IN_PROGRESS (first wave of room 0), or straight to BOSS for a raid. Starts the clock. */
    public List<DungeonRunEvent> onStart(long now) {
        require(RunState.READY, "onStart");
        advanceClock(now);
        startTick = now;
        List<DungeonRunEvent> events = new ArrayList<>();
        events.add(new DungeonRunEvent.Started(now, policy.timeLimitEnabled() ? now + timeLimitTicks : Long.MAX_VALUE));
        if (waves.isEmpty()) {
            state = RunState.BOSS;
            events.add(new DungeonRunEvent.StartBoss(bossId));
        } else {
            state = RunState.IN_PROGRESS;
            enterRoom(0, events);
        }
        return List.copyOf(events);
    }

    /**
     * A wave monster of {@code roomIndex} died or was removed otherwise (despawn, void): call it for every one, or the
     * wave never completes. Kills of other rooms, extra kills and kills outside IN_PROGRESS are ignored.
     */
    public List<DungeonRunEvent> onMonsterKilled(int roomIndex) {
        if (state != RunState.IN_PROGRESS || roomIndex != room || remainingInWave <= 0) {
            return List.of();
        }
        remainingInWave--;
        if (remainingInWave > 0) {
            return List.of();
        }
        List<DungeonRunEvent> events = new ArrayList<>();
        if (wave + 1 < waves.get(room).size()) {
            startWave(wave + 1, events);
        } else {
            events.add(new DungeonRunEvent.OpenDoor(room));
            if (room + 1 < waves.size()) {
                enterRoom(room + 1, events);
            } else {
                state = RunState.BOSS;
                room = -1;
                wave = -1;
                events.add(new DungeonRunEvent.StartBoss(bossId));
            }
        }
        return List.copyOf(events);
    }

    /**
     * BOSS -> CLEARED. A kill at or after the deadline does not count (the time limit wins, whatever order the
     * platform reports both in). Ignored outside BOSS.
     */
    public List<DungeonRunEvent> onBossDefeated(long now) {
        if (state != RunState.BOSS) {
            return List.of();
        }
        advanceClock(now);
        if (timeIsUp(now)) {
            return fail(FailureReason.TIME_LIMIT);
        }
        state = RunState.CLEARED;
        endTick = now;
        return List.of(new DungeonRunEvent.Cleared(now - startTick, startTick, now));
    }

    /** A member died (non-members and deaths outside the fight are ignored). May fail the run (death limit). */
    public List<DungeonRunEvent> onPlayerDied(UUID member) {
        if (!state.isRunning() || !members.contains(member)) {
            return List.of();
        }
        deaths++;
        if (policy.deathLimit() > 0 && deaths >= policy.deathLimit()) {
            return fail(FailureReason.TOO_MANY_DEATHS);
        }
        return List.of();
    }

    /** A member left the instance (logout, teleport out). Whether the run fails is decided in {@link #tick(long)}. */
    public List<DungeonRunEvent> onPlayerLeft(UUID member) {
        if (members.contains(member) && !state.isFinished()) {
            absent.add(member);
        }
        return List.of();
    }

    /** A member who left came back (reconnected). */
    public List<DungeonRunEvent> onPlayerReturned(UUID member) {
        absent.remove(member);
        return List.of();
    }

    /** Once per server tick: time limit and empty-instance checks. */
    public List<DungeonRunEvent> tick(long now) {
        advanceClock(now);
        if (state.isFinished()) {
            return List.of();
        }
        if (timeIsUp(now)) {
            return fail(FailureReason.TIME_LIMIT);
        }
        if (policy.failWhenEveryoneLeft() && absent.size() == members.size()) {
            if (emptySince == NONE) {
                emptySince = now;
            }
            if (now - emptySince >= policy.emptyGraceTicks()) {
                return fail(FailureReason.EVERYONE_LEFT);
            }
        } else {
            emptySince = NONE;
        }
        return List.of();
    }

    /**
     * Tears the run down: -> CLOSED. A run that had not finished fails first ({@link FailureReason#ABORTED}). Release
     * the instance slot after the members are out and the blocks are cleared.
     */
    public List<DungeonRunEvent> close(long now) {
        if (state == RunState.CLOSED) {
            return List.of();
        }
        advanceClock(now);
        List<DungeonRunEvent> events = state.isFinished() ? List.of() : fail(FailureReason.ABORTED);
        state = RunState.CLOSED;
        return events;
    }

    private void enterRoom(int index, List<DungeonRunEvent> events) {
        room = index;
        startWave(0, events);
    }

    private void startWave(int index, List<DungeonRunEvent> events) {
        wave = index;
        List<MonsterCount> monsters = waves.get(room).get(index);
        int total = 0;
        for (MonsterCount monster : monsters) {
            total += monster.count();
        }
        remainingInWave = total;
        events.add(new DungeonRunEvent.SpawnWave(room, index, waves.get(room).size(), monsters));
    }

    private boolean timeIsUp(long now) {
        return state.isRunning() && policy.timeLimitEnabled() && now - startTick >= timeLimitTicks;
    }

    private List<DungeonRunEvent> fail(FailureReason reason) {
        state = RunState.FAILED;
        failure = reason;
        endTick = lastTick;
        room = -1;
        wave = -1;
        remainingInWave = 0;
        return List.of(new DungeonRunEvent.Failed(reason));
    }

    private void advanceClock(long now) {
        if (lastTick != NONE && now < lastTick) {
            throw new IllegalArgumentException("time went backwards: " + now + " < " + lastTick);
        }
        lastTick = now;
    }

    private void require(RunState expected, String action) {
        if (state != expected) {
            throw new IllegalStateException(action + " is not allowed in state " + state);
        }
    }

    // ------------------------------------------------------------------ queries

    public RunState state() {
        return state;
    }

    /** The dungeon id ("d1".."d8") or raid id ("final"). */
    public String instanceId() {
        return instanceId;
    }

    public String bossId() {
        return bossId;
    }

    /** The fixed members, in the order given. */
    public Set<UUID> members() {
        return members;
    }

    public boolean isMember(UUID player) {
        return members.contains(player);
    }

    /** Members currently inside the instance. */
    public Set<UUID> presentMembers() {
        Set<UUID> present = new LinkedHashSet<>(members);
        present.removeAll(absent);
        return Collections.unmodifiableSet(present);
    }

    public int deaths() {
        return deaths;
    }

    public int roomCount() {
        return waves.size();
    }

    /** Waves of a room. */
    public int waveCount(int roomIndex) {
        return waves.get(roomIndex).size();
    }

    /** The scaled monsters of one wave (what {@link DungeonRunEvent.SpawnWave} will contain). */
    public List<MonsterCount> waveMonsters(int roomIndex, int waveIndex) {
        return waves.get(roomIndex).get(waveIndex);
    }

    /** Room being fought, -1 unless IN_PROGRESS. */
    public int currentRoom() {
        return room;
    }

    /** Wave being fought, -1 unless IN_PROGRESS. */
    public int currentWave() {
        return wave;
    }

    /** Monsters of the current wave still alive (0 unless IN_PROGRESS). */
    public int remainingInWave() {
        return remainingInWave;
    }

    public OptionalLong startTick() {
        return startTick == NONE ? OptionalLong.empty() : OptionalLong.of(startTick);
    }

    /** Tick at which the run was cleared or failed. */
    public OptionalLong endTick() {
        return endTick == NONE ? OptionalLong.empty() : OptionalLong.of(endTick);
    }

    /** Ticks since the start (frozen at the end); 0 before the start. */
    public long elapsedTicks(long now) {
        if (startTick == NONE) {
            return 0L;
        }
        return Math.max(0L, (endTick != NONE ? endTick : now) - startTick);
    }

    /** Ticks left before the time limit fails the run; empty when the time limit is disabled. */
    public OptionalLong remainingTicks(long now) {
        if (!policy.timeLimitEnabled()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(0L, timeLimitTicks - elapsedTicks(now)));
    }

    public long timeLimitTicks() {
        return timeLimitTicks;
    }

    public Optional<FailureReason> failureReason() {
        return Optional.ofNullable(failure);
    }
}
