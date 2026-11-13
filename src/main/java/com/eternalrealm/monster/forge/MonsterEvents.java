package com.eternalrealm.monster.forge;

import com.eternalrealm.character.ExperienceSource;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.combat.Combatant;
import com.eternalrealm.monster.MonsterRole;
import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.monster.MonsterStats;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.MonsterThrottle;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Monster rules: their hits deal the damage of their level, they never grief the world or drop vanilla loot and
 * experience, they do not burn in daylight, summons fight for their owner, vanilla monsters do not spawn naturally in
 * the アストリア dimension, and a kill pays EXP (shared with the party), gold and personal loot.
 */
public final class MonsterEvents {
    /** Fire set by the sun is exactly 8 seconds. */
    private static final int SUN_FIRE_TICKS = 160;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private MonsterEvents() {
    }

    /** HIGH priority: sets the monster's damage before the player's defenses reduce it. */
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player) || event.getEntity().level().isClientSide()) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (!MonsterTags.isMonster(attacker)) {
            return;
        }
        double damage = MonsterTags.damage(attacker);
        if (!(damage > 0.0)) {
            return;
        }
        double scale = 0.9 + RANDOM.nextDouble() * 0.2;
        if (attacker instanceof com.eternalrealm.entity.ERMonster monster) {
            scale *= monster.hitMultiplier(); // a special move landing hits harder
        } else {
            scale *= VanillaSpecialMoves.hitMultiplier(attacker);
        }
        if (attacker instanceof Creeper) {
            scale *= Math.min(1.0, event.getAmount() / 20.0); // falls off with the distance like the explosion
        }
        event.setAmount((float) (damage * scale));
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !MonsterTags.isMonster(victim)) {
            return;
        }
        if (victim instanceof Slime slime) {
            slime.setSize(1, false); // no vanilla children
        }
        if (!MonsterTags.givesRewards(victim)) {
            return;
        }
        ServerPlayer killer = killerOf(event.getSource(), victim);
        MonsterTemplate template = MonsterTags.template(victim).orElse(null);
        ServerContext context = ServerContext.current().orElse(null);
        if (killer == null || template == null || context == null) {
            return;
        }
        MonsterStats stats = MonsterScaling.stats(template, MonsterTags.level(victim));
        // a purified field (its calamity was subdued) gives more experience for a while
        long exp = Math.round(stats.exp() * com.eternalrealm.world.forge.FieldThreatService.expMultiplier(victim));
        Map<UUID, Long> shares = context.game().parties().shareExperience(killer, exp, victim.position());
        for (Map.Entry<UUID, Long> share : shares.entrySet()) {
            if (share.getValue() > 0) {
                context.playerData().grantExperience(share.getKey(), share.getValue(), ExperienceSource.MOB_KILL);
            }
        }
        CombatStats killerStats = context.combat().service().get(killer.getUUID()).map(Combatant::stats).orElse(null);
        double goldBonus = killerStats == null ? 0.0 : killerStats.goldBonus();
        double luck = killerStats == null ? 0.0 : killerStats.lootBonus();
        long gold = Math.round(stats.rollGold(RANDOM) * (1.0 + goldBonus));
        if (gold > 0) {
            context.playerData().grantGold(killer.getUUID(), gold);
            killer.playNotifySound(com.eternalrealm.sound.ERSounds.LOOT_GOLD_PICKUP.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                    0.4F, 1.0F);
        }
        killer.displayClientMessage(Component.translatable("message.eternalrealm.monster.reward",
                shares.getOrDefault(killer.getUUID(), 0L), gold).withStyle(ChatFormatting.YELLOW), true);
        LootDrops.drop(killer, victim, template, luck);
        context.game().rankings().onMonsterKill(killer, template.role() == MonsterRole.ELITE);
        context.game().onMonsterKilled(killer, shares.keySet(), template, victim);
    }

    static ServerPlayer killerOf(DamageSource source, LivingEntity victim) {
        Entity entity = source.getEntity();
        if (entity instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            return player;
        }
        LivingEntity credit = victim.getKillCredit();
        return credit instanceof ServerPlayer player && !(player instanceof FakePlayer) ? player : null;
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        if (MonsterTags.isMonster(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (MonsterTags.isMonster(event.getEntity())) {
            event.setDroppedExperience(0);
        }
    }

    /**
     * Before the entity's tick: an idle field monster with no player near skips three ticks in four
     * ({@link MonsterThrottle}); otherwise the sun's fire is put out (phantoms, helmets lost).
     */
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (resting(entity)) {
            event.setCanceled(true);
            return;
        }
        if (entity.getRemainingFireTicks() != SUN_FIRE_TICKS || entity.level().isClientSide()
                || !MonsterTags.isMonster(entity) || entity.isInLava() || !entity.level().isDay()) {
            return;
        }
        if (entity.level().canSeeSky(BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()))) {
            entity.clearFire();
        }
    }

    /** Whether this tick of a field monster is skipped: idle, settled, no player near (cheapest tests first). */
    private static boolean resting(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || MonsterThrottle.ownTick(mob.tickCount, mob.getId())
                || mob.tickCount < MonsterThrottle.SETTLE_TICKS || mob.level().isClientSide()
                || !MonsterTags.isMonster(mob) || !throttleOn()) {
            return false;
        }
        CompoundTag data = mob.getPersistentData();
        if (data.contains("er_instance") || data.hasUUID("er_fight")) {
            return false; // dungeon waves and bosses always run
        }
        List<? extends Player> players = mob.level().players();
        if (players.isEmpty() || !idle(mob)) {
            return false;
        }
        double nearest = Double.MAX_VALUE;
        for (Player player : players) {
            if (!player.isSpectator()) {
                nearest = Math.min(nearest, player.distanceToSqr(mob));
            }
        }
        return MonsterThrottle.skips(mob.tickCount, mob.getId(), true, true, nearest, throttleRange());
    }

    /** Standing still on dry ground with nothing to do: no target, no hurt, no path, no effect, no fire, no attack. */
    private static boolean idle(Mob mob) {
        return mob.getTarget() == null && mob.hurtTime == 0 && !mob.isDeadOrDying() && mob.onGround()
                && !mob.isInWaterOrBubble() && !mob.isInLava() && !mob.isOnFire() && !mob.isPassenger()
                && !mob.isVehicle() && mob.getNavigation().isDone() && mob.getActiveEffects().isEmpty()
                && mob.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4
                && !(mob instanceof com.eternalrealm.entity.ERMonster monster && monster.isActing());
    }

    private static boolean throttleOn() {
        try {
            return com.eternalrealm.config.ERServerConfig.MONSTER_AI_THROTTLE.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static double throttleRange() {
        try {
            return com.eternalrealm.config.ERServerConfig.MONSTER_AI_THROTTLE_RANGE.get();
        } catch (IllegalStateException e) {
            return 32.0;
        }
    }

    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (MonsterTags.isMonster(event.getEntity())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Vexes of a monster evoker fight with half its damage and give nothing. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Vex vex)) {
            return;
        }
        Entity owner = vex.getOwner();
        if (MonsterTags.isMonster(owner)) {
            MonsterTags.mark(vex, MonsterTags.template(owner).map(MonsterTemplate::id).orElse(""),
                    MonsterTags.level(owner), MonsterTags.damage(owner) * 0.5, false);
        }
    }

}
