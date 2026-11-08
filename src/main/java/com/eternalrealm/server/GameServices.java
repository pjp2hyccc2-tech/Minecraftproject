package com.eternalrealm.server;

import com.eternalrealm.job.JobRules;

import java.util.Objects;

/**
 * Gameplay services of the running server (jobs, and from later phases quests, parties, guilds, rankings, the world
 * and dungeons). Created by the bootstrap together with the {@link ServerContext}; server thread only.
 */
public final class GameServices {
    private JobRules jobRules;

    public GameServices(JobRules jobRules) {
        this.jobRules = Objects.requireNonNull(jobRules, "jobRules");
    }

    public JobRules jobRules() {
        return jobRules;
    }

    /** Config reload. */
    public void setJobRules(JobRules rules) {
        this.jobRules = Objects.requireNonNull(rules, "rules");
    }
}
