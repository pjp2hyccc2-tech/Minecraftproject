package com.eternalrealm.job.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.job.ComposedStats;
import com.eternalrealm.job.JobType;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Function;

/**
 * Final stats of an online player: own stats + job + equipment. The equipment module installs the gear reader;
 * until then equipment adds nothing.
 */
public final class PlayerStats {
    /** Stats and secondary modifiers granted by what a player wears. */
    public record Gear(StatBlock stats, CombatModifiers modifiers) {
        public static final Gear NONE = new Gear(StatBlock.ZERO, CombatModifiers.NONE);
    }

    private static volatile Function<ServerPlayer, Gear> gearReader = player -> Gear.NONE;

    private PlayerStats() {
    }

    public static ComposedStats compose(ServerPlayer player, CharacterSnapshot snapshot) {
        Gear gear;
        try {
            gear = gearReader.apply(player);
        } catch (RuntimeException e) {
            gear = Gear.NONE; // never let a broken item break the character
        }
        return ComposedStats.compose(snapshot.totalStats(), snapshot.level(), JobType.fromStored(snapshot.jobId()),
                gear.stats(), gear.modifiers());
    }

    public static void setGearReader(Function<ServerPlayer, Gear> reader) {
        gearReader = reader;
    }
}
