package com.eternalrealm.combat.forge;

import com.eternalrealm.server.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Forge bus handlers of the combat system (server side; the {@code ServerPlayer} checks exclude the client).
 */
public final class CombatEvents {
    private CombatEvents() {
    }

    /** Dodge invulnerability and parries negate the attack entirely (no damage, no hurt animation). */
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> {
                if (context.combat().avoid(player, event.getSource())) {
                    event.setCanceled(true);
                }
            });
        }
    }

    /** Guard and VIT damage reduction. */
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context ->
                    event.setAmount(context.combat().defend(player, event.getSource(), event.getAmount())));
        }
    }

    public static void onKnockBack(LivingKnockBackEvent event) {
        CombatHits.onKnockBack(event);
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context ->
                    event.setStrength(event.getStrength() * context.combat().knockbackFactor(player)));
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.combat().onDeath(player));
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.combat().onRespawn(player, event.isEndConquered()));
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.combat().onDimensionChanged(player));
        }
    }
}
