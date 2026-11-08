package com.eternalrealm.social.forge;

import com.eternalrealm.job.JobType;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.Optional;

/**
 * Shows the guild tag and job emblem above every player's head for everyone: vanilla scoreboard teams sync a prefix
 * to all clients, so each (guild tag, job) combination gets a small team {@code er_<hash>}. Players already in a team
 * that does not belong to the mod (set by an operator) are left alone.
 */
public final class NameplateTeams {
    private static final String PREFIX = "er_";

    private NameplateTeams() {
    }

    public static void update(ServerPlayer player) {
        Optional<ServerContext> context = ServerContext.current();
        if (context.isEmpty()) {
            return;
        }
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (current != null && !current.getName().startsWith(PREFIX)) {
            return;
        }
        JobType job = context.get().playerData().snapshot(player.getUUID())
                .map(snapshot -> JobType.fromStored(snapshot.jobId())).orElse(JobType.NONE);
        Optional<String> tag = context.get().game().guilds().tagOf(player.getUUID());
        String key = tag.orElse("") + "|" + job.id();
        String name = PREFIX + Integer.toHexString(key.hashCode());
        if (current != null && current.getName().equals(name)) {
            return;
        }
        if (tag.isEmpty() && !job.isChosen()) {
            if (current != null) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
            }
            return;
        }
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
            MutableComponent prefix = Component.empty();
            tag.ifPresent(t -> prefix.append(Component.literal("[" + t + "] ").withStyle(ChatFormatting.GREEN)));
            if (job.isChosen()) {
                prefix.append(Component.literal(JobEmblems.symbol(job)).withStyle(JobEmblems.color(job)));
            }
            team.setPlayerPrefix(prefix);
            team.setAllowFriendlyFire(true);
            team.setSeeFriendlyInvisibles(false);
        }
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }
}
