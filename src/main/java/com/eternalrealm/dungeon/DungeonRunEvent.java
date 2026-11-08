package com.eternalrealm.dungeon;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Objects;

/** What the platform must do after an input to a {@link DungeonRun}, in order. */
public sealed interface DungeonRunEvent permits DungeonRunEvent.Started, DungeonRunEvent.SpawnWave,
        DungeonRunEvent.OpenDoor, DungeonRunEvent.StartBoss, DungeonRunEvent.Cleared, DungeonRunEvent.Failed {

    /**
     * The run started: open the entrance door, show the timer.
     *
     * @param deadlineTick tick at which the time limit fails the run, {@link Long#MAX_VALUE} if disabled
     */
    record Started(long startTick, long deadlineTick) implements DungeonRunEvent {
        /** Message, argument 1: dungeon name. */
        public static final String MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".dungeon.started";
    }

    /**
     * Spawn these monsters at the room's spawn points (tag each with the instance and room; report every death or
     * removal with {@link DungeonRun#onMonsterKilled(int)}).
     *
     * @param waveCount number of waves of this room (for "wave 2/3" displays)
     */
    record SpawnWave(int room, int wave, int waveCount, List<MonsterCount> monsters) implements DungeonRunEvent {
        /** Message, argument 1: wave number (1-based), 2: wave count. */
        public static final String MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".dungeon.wave";

        public SpawnWave {
            monsters = List.copyOf(monsters);
            if (monsters.isEmpty() || wave < 0 || wave >= waveCount || room < 0) {
                throw new IllegalArgumentException("invalid wave " + room + "/" + wave + "/" + waveCount);
            }
        }

        public int total() {
            int total = 0;
            for (MonsterCount monster : monsters) {
                total += monster.count();
            }
            return total;
        }
    }

    /** Room {@code room} was cleared: replace its exit door with air. */
    record OpenDoor(int room) implements DungeonRunEvent {
        public static final String MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".dungeon.door_opened";
    }

    /** Spawn the boss at the arena's boss spawn and create its brain. */
    record StartBoss(String bossId) implements DungeonRunEvent {
        /** Message, argument 1: boss name. */
        public static final String MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".dungeon.boss";

        public StartBoss {
            Check.id("bossId", bossId);
        }
    }

    /** The boss died: rewards, records (time attack uses {@code durationTicks}), open the exit. */
    record Cleared(long durationTicks, long startTick, long endTick) implements DungeonRunEvent {
        /** Message, argument 1: dungeon name, 2: formatted time. */
        public static final String MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".dungeon.cleared";
    }

    /** The run failed: tell the members ({@link FailureReason#messageKey()}) and send them out. */
    record Failed(FailureReason reason) implements DungeonRunEvent {
        public Failed {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
