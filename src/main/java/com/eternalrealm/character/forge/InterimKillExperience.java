package com.eternalrealm.character.forge;

import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.server.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * INTERIM experience source (Phase 1): killing a hostile vanilla mob grants {@code maxHealth * multiplier} EXP so
 * levelling can be play-tested before the monster system exists.
 *
 * <p>TODO(phase: monsters & bosses): replace with per-monster EXP definitions, level-difference scaling,
 * party sharing and anti-farming rules; then remove this class and its config section.</p>
 */
public final class InterimKillExperience {
    private static final long MAX_EXP_PER_KILL = 100_000L;

    private InterimKillExperience() {
    }

    /** Registered with LOWEST priority and without receiving cancelled events. */
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        // LivingDeathEvent also fires on the client; never touch server config or state there.
        if (victim.level().isClientSide()) {
            return;
        }
        if (!(victim instanceof Enemy) || com.eternalrealm.monster.forge.MonsterTags.isMonster(victim)) {
            return; // monsters of the monster module pay their own EXP (MonsterEvents)
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer instanceof FakePlayer) {
            return;
        }
        if (!ERServerConfig.MOB_KILL_EXP_ENABLED.get()) {
            return;
        }
        long amount = Math.round(victim.getMaxHealth() * ERServerConfig.MOB_KILL_EXP_PER_MAX_HEALTH.get());
        if (amount <= 0) {
            return;
        }
        long clamped = Math.min(amount, MAX_EXP_PER_KILL);
        ServerContext.current().ifPresent(context ->
                context.playerData().grantExperience(killer.getUUID(), clamped, ExperienceSource.MOB_KILL));
    }
}
