package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.forge.BossFight;
import com.eternalrealm.server.ServerContext;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Forge events of dungeons and boss fights. */
public final class DungeonEvents {
    private DungeonEvents() {
    }

    /** HIGH priority: boss melee damage before the player's defenses; hits on a boss go into the fight's pool. */
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        if (victim.getTags().contains(BossFight.TAG)) {
            context.game().dungeons().fightOf(victim).ifPresent(fight -> {
                if (!fight.isKilling()) {
                    fight.absorb(event.getSource().getEntity(), event.getAmount());
                    event.setAmount(0.0F);
                }
            });
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (victim instanceof Player && attacker != null && attacker.getTags().contains(BossFight.TAG)) {
            context.game().dungeons().fightOf(attacker).flatMap(fight -> {
                java.util.OptionalDouble damage = fight.meleeDamage(victim);
                return damage.isPresent() ? java.util.Optional.of(damage.getAsDouble()) : java.util.Optional.<Double>empty();
            }).ifPresent(damage -> event.setAmount(damage.floatValue()));
        }
    }

    /** Phase transitions and shields make the boss untouchable. */
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !victim.getTags().contains(BossFight.TAG)) {
            return;
        }
        ServerContext.current().flatMap(context -> context.game().dungeons().fightOf(victim)).ifPresent(fight -> {
            if (fight.isInvulnerable() && !fight.isDefeated()) {
                event.setCanceled(true);
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BLOCK,
                        SoundSource.HOSTILE, 0.6F, 1.4F);
            }
        });
    }

    /** Damage a member takes during the boss fight counts for the merit ranking and the hero (after armour and guards). */
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide() && event.getAmount() > 0.0F) {
            ServerContext.current().flatMap(context -> context.game().dungeons().fightFor(player))
                    .ifPresent(fight -> fight.recordTaken(player, Math.min(event.getAmount(), player.getHealth())));
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        ServerContext.current().ifPresent(context -> {
            if (entity instanceof ServerPlayer player) {
                context.game().dungeons().onPlayerDeath(player);
            } else {
                context.game().dungeons().onEntityDeath(entity);
            }
        });
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity().getTags().contains(BossFight.TAG)) {
            event.setCanceled(true);
        }
    }

    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(BossFight.TAG)) {
            event.setDroppedExperience(0);
        }
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.isEndConquered()) {
            ServerContext.current().ifPresent(context -> context.game().dungeons().onRespawn(player));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerContext.current().ifPresent(context -> context.game().dungeons().onLogin(player));
        }
    }

    /** {@code /dungeon leave}: give up and go back. */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("dungeon").then(Commands.literal("leave").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            ServerContext.current().ifPresent(context -> context.game().dungeons().leave(player));
            return 1;
        })));
    }
}
