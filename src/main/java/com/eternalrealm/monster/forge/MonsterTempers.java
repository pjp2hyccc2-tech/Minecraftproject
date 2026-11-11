package com.eternalrealm.monster.forge;

import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.monster.Temper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The calm field monsters ({@link Temper}): they pick no fight with a player until that player hurts them or comes
 * within the aggro radius (5 blocks by default; a few blocks farther they turn to look and growl), and a calm one deals
 * no damage (no touch damage of slimes either). Every way a monster picks a target goes through {@link Mob#setTarget},
 * so vetoing the target change covers vanilla goals, the mod's own and the spawner's aggro alike. Server thread only.
 */
public final class MonsterTempers {
    /** Persistent tag of a monster that is calm until provoked (habitat monsters). */
    public static final String CALM_TAG = "er_calm";
    /** How often the field monsters look around (ticks; the spawner calls {@link #look} at this pace). */
    public static final int LOOK_STEP = 10;
    /** Blocks beyond the aggro radius where a calm monster warns a player (turns to look, growls). */
    public static final double WARN_MARGIN = 3.0;

    private static final Map<UUID, Temper> TEMPERS = new HashMap<>();

    private MonsterTempers() {
    }

    /** Marks a freshly spawned habitat monster as calm until provoked. */
    public static void makeCalm(Mob mob) {
        mob.getPersistentData().putBoolean(CALM_TAG, true);
    }

    /** A monster with a temper: a habitat monster while calm habitats are switched on. */
    public static boolean hasTemper(Entity entity) {
        return entity instanceof Mob && entity.getPersistentData().getBoolean(CALM_TAG)
                && ERServerConfig.CALM_HABITATS.get();
    }

    private static Temper temper(Mob mob) {
        return TEMPERS.computeIfAbsent(mob.getUUID(), id -> new Temper());
    }

    /** Whether {@code mob} may fight {@code player} (always, for monsters without a temper). */
    public static boolean mayFight(Mob mob, Player player) {
        return !hasTemper(mob) || temper(mob).mayTarget(player.getUUID());
    }

    /** A calm monster turns only on the players who provoked it. */
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide()
                && event.getNewTarget() instanceof Player player && !mayFight(mob, player)) {
            event.setCanceled(true);
            if (mob instanceof net.minecraft.world.entity.monster.EnderMan && mob.getTarget() == null) {
                mob.setTarget(null); // it set its scream and rush before asking: take them back
            }
        }
    }

    /**
     * Hurting a calm monster provokes it (it turns on the attacker); a monster that is still calm deals no damage to
     * players (touch damage, a stray swing).
     */
    public static void onLivingAttack(LivingAttackEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (source instanceof ServerPlayer player && victim instanceof Mob mob && hasTemper(mob) && !player.isSpectator()) {
            if (temper(mob).provoke(player.getUUID())) {
                angry(mob);
            }
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                mob.setTarget(player);
            }
            return;
        }
        if (victim instanceof Player && source instanceof Mob mob && hasTemper(mob) && temper(mob).isCalm()) {
            event.setCanceled(true);
        }
    }

    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) {
            TEMPERS.remove(event.getEntity().getUUID());
        }
    }

    public static void clear() {
        TEMPERS.clear();
    }

    /**
     * One look around of field monsters ({@link #LOOK_STEP} ticks after the last): a player within the aggro radius
     * and in sight is attacked at once; a few blocks farther the monster turns to look and growls.
     */
    public static void look(ServerLevel level, List<Mob> monsters, List<ServerPlayer> players) {
        if (!ERServerConfig.CALM_HABITATS.get()) {
            return;
        }
        double aggro = ERServerConfig.AGGRO_RADIUS.get();
        double warn = aggro > 0.0 ? aggro + WARN_MARGIN : 0.0;
        for (Mob mob : monsters) {
            if (!hasTemper(mob) || !mob.isAlive()) {
                continue;
            }
            List<UUID> close = new ArrayList<>();
            List<UUID> near = new ArrayList<>();
            Map<UUID, ServerPlayer> byId = new HashMap<>();
            for (ServerPlayer player : players) {
                if (player.isCreative() || player.isSpectator() || !player.isAlive()) {
                    continue;
                }
                double distance = mob.distanceToSqr(player);
                if (distance > warn * warn || !mob.hasLineOfSight(player)) {
                    continue;
                }
                (distance <= aggro * aggro ? close : near).add(player.getUUID());
                byId.put(player.getUUID(), player);
            }
            boolean fighting = mob.getTarget() != null && mob.getTarget().isAlive();
            Temper.Look look = temper(mob).look(close, near, LOOK_STEP, fighting);
            if (look.warned() != null) {
                ServerPlayer player = byId.get(look.warned());
                mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
                mob.playAmbientSound();
            }
            if (look.provoked() != null) {
                ServerPlayer player = byId.get(look.provoked());
                angry(mob);
                if (!fighting) {
                    mob.setTarget(player);
                }
            }
        }
    }

    /** The moment a calm monster turns hostile: a puff of anger over its head. */
    private static void angry(Mob mob) {
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.4,
                    mob.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
        }
    }
}
