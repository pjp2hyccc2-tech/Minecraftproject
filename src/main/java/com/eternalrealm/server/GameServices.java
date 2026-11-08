package com.eternalrealm.server;

import com.eternalrealm.job.JobRules;
import com.eternalrealm.ranking.forge.RankingService;
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
    private final RankingService rankings;

    public GameServices(JobRules jobRules, PartyManager parties, GuildManager guilds, RankingService rankings) {
        this.jobRules = Objects.requireNonNull(jobRules, "jobRules");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.guilds = Objects.requireNonNull(guilds, "guilds");
        this.rankings = Objects.requireNonNull(rankings, "rankings");
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

    public RankingService rankings() {
        return rankings;
    }

    /** A player leaves: flush what lives in the character before it is saved. */
    public void onLogout(java.util.UUID playerId) {
        rankings.onLogout(playerId);
        parties.onLogout(playerId);
    }

    /** Every server tick (after player data and combat). */
    public void tick(long tick) {
        parties.tick();
        if (tick % 20 == 0) {
            guilds.tickSecond();
            rankings.tickSecond();
        }
    }

    /** Server stopped: flush what must survive. */
    public void shutdown() {
        rankings.flush();
        guilds.flush();
    }
}
