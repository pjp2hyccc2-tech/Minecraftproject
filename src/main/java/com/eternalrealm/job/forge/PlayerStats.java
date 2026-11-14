package com.eternalrealm.job.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.StatBlock;
import com.eternalrealm.character.StatPercents;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.job.ComposedStats;
import com.eternalrealm.job.JobType;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Function;

/**
 * Final stats of an online player: own stats + job + learned passive skills + equipment. The equipment module installs
 * the gear reader; until then equipment adds nothing.
 */
public final class PlayerStats {
    /** Flat stats, stat percentages and secondary modifiers granted by what a player wears. */
    public record Gear(StatBlock stats, StatPercents percents, CombatModifiers modifiers) {
        public static final Gear NONE = new Gear(StatBlock.ZERO, StatPercents.ZERO, CombatModifiers.NONE);
    }

    private static volatile Function<ServerPlayer, Gear> gearReader = player -> Gear.NONE;
    private static volatile Function<ServerPlayer, com.eternalrealm.job.PassiveTotals> perksReader =
            player -> com.eternalrealm.job.PassiveTotals.NONE;

    private PlayerStats() {
    }

    public static ComposedStats compose(ServerPlayer player, CharacterSnapshot snapshot) {
        Gear gear;
        try {
            gear = gearReader.apply(player);
        } catch (RuntimeException e) {
            gear = Gear.NONE; // never let a broken item break the character
        }
        JobType job = JobType.fromStored(snapshot.jobId());
        return ComposedStats.compose(snapshot.totalStats(), snapshot.level(), job, gear.stats(), gear.percents(),
                gear.modifiers(),
                com.eternalrealm.job.SkillTree.passives(com.eternalrealm.job.JobPath.of(snapshot.jobId()),
                        snapshot.level(), snapshot.skillInvestments()).modifiers());
    }

    public static void setGearReader(Function<ServerPlayer, Gear> reader) {
        gearReader = reader;
    }

    /**
     * What the worn named equipment adds when hits land (weapon perks and element, set bonus perks); resolved by the
     * server only. Never fails: a broken item adds nothing.
     */
    public static com.eternalrealm.job.PassiveTotals perks(ServerPlayer player) {
        try {
            com.eternalrealm.job.PassiveTotals perks = perksReader.apply(player);
            return perks == null ? com.eternalrealm.job.PassiveTotals.NONE : perks;
        } catch (RuntimeException e) {
            return com.eternalrealm.job.PassiveTotals.NONE;
        }
    }

    public static void setPerksReader(Function<ServerPlayer, com.eternalrealm.job.PassiveTotals> reader) {
        perksReader = reader;
    }
}
