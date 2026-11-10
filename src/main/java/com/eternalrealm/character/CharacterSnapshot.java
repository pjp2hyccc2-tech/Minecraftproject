package com.eternalrealm.character;

import com.eternalrealm.season.SeasonId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read-only view of a character sent to the owning client (HUD, journal) and used by commands.
 * The client never computes game state; it only displays the latest snapshot from the server.
 *
 * @param experienceToNext   0 when the character is at the level cap
 * @param totalStats         base + allocated (job and equipment bonuses are added by the job module)
 * @param jobId              {@link CharacterRecord#NO_JOB} until a job is chosen
 * @param unspentSkillPoints skill points not invested yet
 * @param skillInvestments   points invested per skill id
 * @param skillLoadout       the stored skill loadout ({@code ""} = empty slot; an empty list = the job's default)
 */
public record CharacterSnapshot(SeasonId seasonId, int level, int levelCap, long experience, long experienceToNext,
                                int unspentStatPoints, StatBlock allocatedStats, StatBlock totalStats, String jobId,
                                long gold, int unspentSkillPoints, Map<String, Integer> skillInvestments,
                                List<String> skillLoadout) {
    public CharacterSnapshot {
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(allocatedStats, "allocatedStats");
        Objects.requireNonNull(totalStats, "totalStats");
        if (level < 1 || level > CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("level out of range: " + level);
        }
        if (levelCap < 1 || levelCap > CharacterRules.MAX_LEVEL) {
            throw new IllegalArgumentException("levelCap out of range: " + levelCap);
        }
        if (experience < 0 || experienceToNext < 0 || unspentStatPoints < 0 || unspentSkillPoints < 0) {
            throw new IllegalArgumentException("negative progression value");
        }
        CharacterRecord.checkJobId(jobId);
        if (gold < 0 || gold > CharacterRecord.MAX_GOLD) {
            throw new IllegalArgumentException("gold out of range: " + gold);
        }
        skillInvestments = CharacterRecord.copySkills(skillInvestments);
        skillLoadout = CharacterRecord.copyLoadout(skillLoadout);
    }

    /** A snapshot with the default skill loadout. */
    public CharacterSnapshot(SeasonId seasonId, int level, int levelCap, long experience, long experienceToNext,
                             int unspentStatPoints, StatBlock allocatedStats, StatBlock totalStats, String jobId,
                             long gold, int unspentSkillPoints, Map<String, Integer> skillInvestments) {
        this(seasonId, level, levelCap, experience, experienceToNext, unspentStatPoints, allocatedStats, totalStats,
                jobId, gold, unspentSkillPoints, skillInvestments, List.of());
    }

    /** A snapshot without job, gold or skills. */
    public CharacterSnapshot(SeasonId seasonId, int level, int levelCap, long experience, long experienceToNext,
                             int unspentStatPoints, StatBlock allocatedStats, StatBlock totalStats) {
        this(seasonId, level, levelCap, experience, experienceToNext, unspentStatPoints, allocatedStats, totalStats,
                CharacterRecord.NO_JOB, 0L, 0, Map.of(), List.of());
    }

    public boolean atCap() {
        return level >= levelCap;
    }

    public boolean hasJob() {
        return !CharacterRecord.NO_JOB.equals(jobId);
    }

    /** Points invested in {@code skillId} (0 if none). */
    public int investment(String skillId) {
        return skillInvestments.getOrDefault(skillId, 0);
    }

    /** @return progress towards the next level in {@code [0, 1]}; 1 at the cap */
    public double progress() {
        if (atCap() || experienceToNext <= 0) {
            return 1.0;
        }
        return Math.min(1.0, (double) experience / (double) experienceToNext);
    }
}
