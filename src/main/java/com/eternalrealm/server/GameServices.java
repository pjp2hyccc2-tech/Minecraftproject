package com.eternalrealm.server;

import com.eternalrealm.job.JobRules;
import com.eternalrealm.social.forge.GuildManager;
import com.eternalrealm.social.forge.PartyManager;

import java.util.Objects;

/**
 * Gameplay services of the running server (jobs, parties, guilds, and from later phases quests, rankings, the world and
 * dungeons). Created by the bootstrap together with the {@link ServerContext}; server thread only.
 */
public final class GameServices {
    private JobRules jobRules;
    private final PartyManager parties;
    private final GuildManager guilds;

    public GameServices(JobRules jobRules, PartyManager parties, GuildManager guilds) {
        this.jobRules = Objects.requireNonNull(jobRules, "jobRules");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.guilds = Objects.requireNonNull(guilds, "guilds");
    }

    public JobRules jobRules() {
        return jobRules;
    }

    /** Config reload. */
    public void setJobRules(JobRules rules) {
        this.jobRules = Objects.requireNonNull(rules, "rules");
    }

    public PartyManager parties() {
        return parties;
    }

    public GuildManager guilds() {
        return guilds;
    }

    /** Every server tick (after player data and combat). */
    public void tick(long tick) {
        parties.tick();
        if (tick % 20 == 0) {
            guilds.tickSecond();
        }
    }

    /** Server stopped: flush what must survive. */
    public void shutdown() {
        guilds.flush();
    }
}
