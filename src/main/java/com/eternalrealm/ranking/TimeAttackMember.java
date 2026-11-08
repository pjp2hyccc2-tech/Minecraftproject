package com.eternalrealm.ranking;

import java.util.Objects;
import java.util.UUID;

/**
 * A participant of a boss time-attack run as recorded by the server when the fight started.
 *
 * @param name  display name at the time of the run
 * @param jobId job (class) id, or {@link #NO_JOB} before the job system exists
 * @param level character level at the start of the fight
 */
public record TimeAttackMember(UUID id, String name, String jobId, int level) {
    public static final String NO_JOB = "none";

    public TimeAttackMember {
        Objects.requireNonNull(id, "id");
        Check.displayName("name", name);
        Check.id("jobId", jobId);
        Check.range("level", level, 1, Integer.MAX_VALUE);
    }
}
