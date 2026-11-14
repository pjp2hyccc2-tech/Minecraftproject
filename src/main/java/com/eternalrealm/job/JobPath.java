package com.eternalrealm.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Where a character stands on its job's road: the job, and from level {@value JobClass#LEVEL} the class it grew into
 * ({@code null} before). Read from the stored job id ({@link #of}): a job's id ({@code "knight"}) or a class's
 * ({@code "guardian"}). The skills a character may learn and use are the job's and the class's ({@link #owns}). Pure.
 */
public record JobPath(JobType job, JobClass advanced) {
    public static final JobPath NONE = new JobPath(JobType.NONE, null);

    public JobPath {
        Objects.requireNonNull(job, "job");
        if (advanced != null && advanced.base() != job) {
            throw new IllegalArgumentException(advanced + " is not a class of " + job);
        }
    }

    /** The path of a stored job id; unknown ids are {@link #NONE}. */
    public static JobPath of(String storedJobId) {
        if (storedJobId == null) {
            return NONE;
        }
        return JobClass.byId(storedJobId).map(cls -> new JobPath(cls.base(), cls))
                .orElseGet(() -> new JobPath(JobType.fromStored(storedJobId), null));
    }

    /** A job before its class. */
    public static JobPath base(JobType job) {
        return new JobPath(job, null);
    }

    public boolean isAdvanced() {
        return advanced != null;
    }

    /** The id stored for this path: the class's once advanced, else the job's. */
    public String storedId() {
        return advanced != null ? advanced.id() : job.id();
    }

    /** The name shown for it: the class's once advanced ("守護士"), else the job's ("騎士"). */
    public String translationKey() {
        return advanced != null ? advanced.translationKey() : job.translationKey();
    }

    /** Whether a character on this path may learn and use {@code skill}: the job's skills and its own class's. */
    public boolean owns(SkillDefinition skill) {
        return job.isChosen() && skill.job() == job && (skill.jobClass() == null || skill.jobClass() == advanced);
    }

    /** Every skill of the path: the job's tree, then the class's. */
    public List<SkillDefinition> skills() {
        if (!job.isChosen()) {
            return List.of();
        }
        if (advanced == null) {
            return Jobs.get(job).skills();
        }
        List<SkillDefinition> all = new ArrayList<>(Jobs.get(job).skills());
        all.addAll(Jobs.classSkills(advanced));
        return Collections.unmodifiableList(all);
    }
}
