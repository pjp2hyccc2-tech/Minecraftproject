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
    private final com.eternalrealm.world.forge.WorldService world;
    private final com.eternalrealm.quest.forge.QuestService quests;
    private final com.eternalrealm.monster.forge.MonsterSpawner monsters;
    private final com.eternalrealm.dungeon.forge.DungeonService dungeons;
    private final com.eternalrealm.story.forge.StoryDirector story;
    private final com.eternalrealm.economy.forge.MarketService market;

    public GameServices(JobRules jobRules, PartyManager parties, GuildManager guilds, RankingService rankings,
                        com.eternalrealm.world.forge.WorldService world, com.eternalrealm.quest.forge.QuestService quests,
                        com.eternalrealm.monster.forge.MonsterSpawner monsters,
                        com.eternalrealm.dungeon.forge.DungeonService dungeons,
                        com.eternalrealm.story.forge.StoryDirector story,
                        com.eternalrealm.economy.forge.MarketService market) {
        this.jobRules = Objects.requireNonNull(jobRules, "jobRules");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.guilds = Objects.requireNonNull(guilds, "guilds");
        this.rankings = Objects.requireNonNull(rankings, "rankings");
        this.world = Objects.requireNonNull(world, "world");
        this.quests = Objects.requireNonNull(quests, "quests");
        this.monsters = Objects.requireNonNull(monsters, "monsters");
        this.dungeons = Objects.requireNonNull(dungeons, "dungeons");
        this.story = Objects.requireNonNull(story, "story");
        this.market = Objects.requireNonNull(market, "market");
    }

    public com.eternalrealm.economy.forge.MarketService market() {
        return market;
    }

    public com.eternalrealm.story.forge.StoryDirector story() {
        return story;
    }

    public com.eternalrealm.dungeon.forge.DungeonService dungeons() {
        return dungeons;
    }

    public com.eternalrealm.world.forge.WorldService world() {
        return world;
    }

    public com.eternalrealm.quest.forge.QuestService quests() {
        return quests;
    }

    /** A monster of the monster module died: kills count for the killer and the party members who shared the EXP. */
    public void onMonsterKilled(net.minecraft.server.level.ServerPlayer killer, java.util.Set<java.util.UUID> credited,
                                com.eternalrealm.monster.MonsterTemplate template,
                                net.minecraft.world.entity.LivingEntity victim) {
        int areaId = victim.level() instanceof net.minecraft.server.level.ServerLevel level
                && com.eternalrealm.world.forge.Realm.isRealm(level)
                ? com.eternalrealm.world.forge.Realm.areaAt(level, victim.getX(), victim.getZ()).id() : 0;
        com.eternalrealm.quest.GameEvent event = new com.eternalrealm.quest.GameEvent.MonsterKilled(template.id(), areaId);
        for (java.util.UUID id : credited) {
            net.minecraft.server.level.ServerPlayer player = killer.server.getPlayerList().getPlayer(id);
            if (player != null) {
                quests.event(player, event);
            }
        }
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
        dungeons.onLogout(playerId);
        story.onLogout(playerId);
        com.eternalrealm.sound.forge.MusicContexts.forget(playerId);
        com.eternalrealm.dungeon.forge.GatekeeperService.forget(playerId);
        quests.onLogout(playerId);
        world.onLogout(playerId);
        rankings.onLogout(playerId);
        parties.onLogout(playerId);
    }

    /** Every server tick (after player data and combat). */
    public void tick(long tick) {
        parties.tick();
        monsters.tick(tick);
        dungeons.tick(tick);
        story.tick(tick);
        if (tick % 20 == 0) {
            guilds.tickSecond();
            rankings.tickSecond();
            world.tickSecond();
            quests.tickSecond();
            market.tickSecond();
            ServerContext.current().ifPresent(context ->
                    com.eternalrealm.sound.forge.MusicContexts.tickSecond(context.server(), context));
        }
    }

    /** Server stopped: flush what must survive. */
    public void shutdown() {
        dungeons.shutdown();
        rankings.flush();
        guilds.flush();
        world.flush();
        market.flush();
    }
}
