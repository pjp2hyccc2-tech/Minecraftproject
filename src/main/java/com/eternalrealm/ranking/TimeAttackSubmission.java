package com.eternalrealm.ranking;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Result of submitting a boss clear to the time attack ({@link RankingBoard#submitTimeAttack}). The flags tell the
 * caller what to announce; a rejected run changes nothing and has no flags.
 *
 * @param validation    whether the run was accepted, and why not
 * @param bracketRecord the run is now the fastest of its boss, difficulty and party bracket
 * @param partyBest     the run is now the best of its party (the same set of members); only party bests are kept
 * @param personalBests members whose personal best for this boss and difficulty improved, in member order
 * @param firstClear    the run is the season's first accepted clear of this boss (any difficulty)
 */
public record TimeAttackSubmission(TimeAttackValidator.Validation validation, boolean bracketRecord, boolean partyBest,
                                   List<UUID> personalBests, boolean firstClear) {
    /** Args: party member names, boss name, difficulty name. Server-wide announcement of a first clear. */
    public static final String FIRST_CLEAR_MESSAGE = "message." + ModInfo.MOD_ID + ".time_attack.first_clear";
    /** Args: party member names, boss name, difficulty name, bracket name, time. New fastest clear of a bracket. */
    public static final String RECORD_MESSAGE = "message." + ModInfo.MOD_ID + ".time_attack.record";
    /** Args: boss name, difficulty name, time. Sent to a member whose personal best improved. */
    public static final String PERSONAL_BEST_MESSAGE = "message." + ModInfo.MOD_ID + ".time_attack.personal_best";

    public TimeAttackSubmission {
        Objects.requireNonNull(validation, "validation");
        personalBests = List.copyOf(personalBests);
        if (!validation.accepted() && (bracketRecord || partyBest || firstClear || !personalBests.isEmpty())) {
            throw new IllegalArgumentException("A rejected run cannot set records");
        }
        if (!partyBest && (bracketRecord || !personalBests.isEmpty())) {
            throw new IllegalArgumentException("Only a party best can be a bracket record or a personal best");
        }
    }

    public static TimeAttackSubmission rejected(TimeAttackValidator.Validation validation) {
        if (validation.accepted()) {
            throw new IllegalArgumentException("Not a rejection: " + validation);
        }
        return new TimeAttackSubmission(validation, false, false, List.of(), false);
    }

    public boolean accepted() {
        return validation.accepted();
    }
}
