package com.eternalrealm.combat.forge;

import com.eternalrealm.server.ServerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

    /**
     * Players never hurt each other outside PvP matches (ETERNAL REALM is cooperative; vanilla attacks, arrows and
     * the mod's attacks alike). Dodge invulnerability and parries negate the attack entirely (no damage, no hurt
     * animation).
     */
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && event.getSource().getDirectEntity() == attacker && !attacker.level().isClientSide()
                && ERMobEffects.isStunned(attacker)) {
            event.setCanceled(true); // a stunned fighter cannot strike
            return;
        }
        if (event.getEntity() instanceof ServerPlayer victim
                && event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != victim && !PvpRules.canHurt(attacker, victim)) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> {
                if (context.combat().avoid(player, event.getSource())) {
                    event.setCanceled(true);
                }
            });
        }
    }

    /**
     * Skill debuffs on everyone (vulnerable victims take more, weakened attackers deal less), then the guard and the
     * VIT damage reduction of players.
     */
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.level().isClientSide()) {
            double factor = ERMobEffects.vulnerabilityMultiplier(victim);
            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim) {
                factor *= ERMobEffects.weakenMultiplier(attacker);
            }
            if (factor != 1.0) {
                event.setAmount((float) (event.getAmount() * factor));
            }
        }
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
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != event.getEntity()
                && killer.isAlive()) {
            ServerContext.current().ifPresent(context -> {
                double heal = context.combat().passives(killer).healOnKill();
                if (heal > 0.0 && killer.getHealth() < killer.getMaxHealth()) {
                    killer.heal((float) (killer.getMaxHealth() * heal));
                }
            });
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> {
                context.combat().onDeath(player);
                context.game().rankings().onDeath(player);
            });
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.combat().onRespawn(player, event.isEndConquered()));
        }
    }

    /** Non-death clones (leaving the End): keep the bonus and the health. Deaths are handled by the respawn event. */
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            float previousHealth = event.getOriginal().getHealth();
            ServerContext.current().ifPresent(context -> context.combat().onCloned(player, previousHealth));
        }
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.combat().onDimensionChanged(player));
        }
    }
}
