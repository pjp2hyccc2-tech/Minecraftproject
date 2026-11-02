package com.clockworkdiver.combat;

import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.entity.TimeEater;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.interior.DiveSession;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "Tick Strike" combat system. Inside a clock everything moves to the escapement's beat. Actions made
 * on the beat (Just Tick) hit harder, build a combo and charge steam pressure; steam powers the Steam
 * Burst (right click) and the Piston Dash (sneak + right click).
 */
public final class TickStrike {
    public static final float BURST_COST = 40.0f;
    public static final float BURST_COST_PERFECT = 30.0f;
    public static final float DASH_COST = 25.0f;

    private static final class State {
        float steam;
        int combo;
        @Nullable
        Quality pending;
        long pendingTime = Long.MIN_VALUE;
        int burstCooldown;
        int dashCooldown;
        boolean abilityDamage;
        boolean shellHintShown;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private TickStrike() {}

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    public static float maxSteam(PlayerProgress progress) {
        return 100.0f + 25.0f * progress.steamLevel;
    }

    public static void reset(ServerPlayer player) {
        State st = state(player);
        st.steam = 30.0f;
        st.combo = 0;
        st.pending = null;
    }

    public static void clear(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    public static void clearAll() {
        STATES.clear();
    }

    public static void tick(MinecraftServer server) {
        for (State st : STATES.values()) {
            if (st.burstCooldown > 0) st.burstCooldown--;
            if (st.dashCooldown > 0) st.dashCooldown--;
        }
    }

    /** The client reports how close to the beat the attack/use key was pressed. */
    public static void onBeatInput(ServerPlayer player, Quality quality, boolean attack) {
        if (DiveManager.session(player) == null) return;
        State st = state(player);
        st.pending = quality;
        st.pendingTime = player.level().getGameTime();
    }

    /** The judgement for the action being processed right now: the client's report if fresh, else the server's own. */
    public static Quality consumeQuality(ServerPlayer player, DiveSession s) {
        State st = state(player);
        long now = player.level().getGameTime();
        if (st.pending != null && now - st.pendingTime <= 4) {
            Quality q = st.pending;
            st.pending = null;
            return q;
        }
        return Tempo.judge(now, 0.0f, s.anchor + s.offset, s.period, WorldProgress.of(player).lensLevel);
    }

    public static void addSteam(ServerPlayer player, float amount) {
        State st = state(player);
        PlayerProgress progress = WorldProgress.of(player);
        float max = maxSteam(progress);
        float before = st.steam;
        st.steam = Mth.clamp(st.steam + amount, 0.0f, max);
        if ((int) before != (int) st.steam) sync(player, null);
        if (before < BURST_COST && st.steam >= BURST_COST && progress.markSeen(PlayerProgress.HINT_STEAM)) {
            ModNetwork.send(player, new S2C.Hint(List.of("hint.clockworkdiver.steam.0", "hint.clockworkdiver.steam.1")));
        }
    }

    /** A perfectly timed repair action (screw turn, crank) also feeds the combo. */
    public static void onPerfectAction(ServerPlayer player) {
        State st = state(player);
        st.combo++;
        addSteam(player, 8.0f);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.PERFECT.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        sync(player, Quality.PERFECT);
    }

    public static void sync(ServerPlayer player, @Nullable Quality quality) {
        State st = state(player);
        ModNetwork.send(player, new S2C.Combat(st.steam, maxSteam(WorldProgress.of(player)), st.combo, quality == null ? -1 : quality.ordinal()));
    }

    /** Applies the beat bonus to wrench hits inside a clock. */
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player) return;
        DiveSession s = DiveManager.session(player);
        if (s == null) return;
        State st = state(player);
        if (st.abilityDamage) return;
        if (!player.getMainHandItem().is(ModItems.TINKER_WRENCH.get())) return;

        PlayerProgress progress = WorldProgress.of(player);
        Quality q = consumeQuality(player, s);
        float amount = event.getAmount() + progress.wrenchLevel * 1.5f;
        LivingEntity target = event.getEntity();
        if (q == Quality.PERFECT) {
            st.combo++;
            amount *= 1.5f + 0.25f * Math.min(st.combo - 1, 6);
            addSteam(player, 12.0f + 2.0f * Math.min(st.combo, 5));
            player.serverLevel().playSound(null, target.blockPosition(), ModSounds.PERFECT.get(), SoundSource.PLAYERS, 1.0f,
                    1.0f + Math.min(st.combo, 8) * 0.05f);
            player.serverLevel().sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.6), target.getZ(), 14,
                    0.3, 0.3, 0.3, 0.3);
            player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 8,
                    0.2, 0.2, 0.2, 0.4);
        } else if (q == Quality.GOOD) {
            addSteam(player, 5.0f);
        } else {
            st.combo = 0;
            amount *= 0.75f;
            addSteam(player, 2.0f);
        }
        if (target instanceof TimeEater && q != Quality.PERFECT) {
            amount *= 0.3f;
            player.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.4f, 1.8f);
            if (!st.shellHintShown) {
                st.shellHintShown = true;
                player.displayClientMessage(Component.translatable("message.clockworkdiver.boss_shell"), true);
            }
        }
        event.setAmount(amount);
        sync(player, q);
    }

    /** Right click with the wrench: Steam Burst, or Piston Dash while sneaking. */
    public static void useAbility(ServerPlayer player) {
        DiveSession s = DiveManager.session(player);
        if (s == null) {
            player.displayClientMessage(Component.translatable("message.clockworkdiver.steam_outside"), true);
            return;
        }
        State st = state(player);
        Quality q = consumeQuality(player, s);
        PlayerProgress progress = WorldProgress.of(player);
        ServerLevel level = player.serverLevel();
        if (player.isShiftKeyDown()) {
            if (st.dashCooldown > 0) return;
            if (st.steam < DASH_COST) {
                player.displayClientMessage(Component.translatable("message.clockworkdiver.no_steam", (int) st.steam, (int) DASH_COST), true);
                return;
            }
            st.steam -= DASH_COST;
            st.dashCooldown = 12;
            Vec3 look = player.getLookAngle();
            Vec3 dir = new Vec3(look.x, 0.0, look.z);
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0.0, 0.0, 1.0);
            dir = dir.normalize().scale(q == Quality.PERFECT ? 2.1 : 1.6);
            player.setDeltaMovement(dir.x, 0.25, dir.z);
            player.hurtMarked = true;
            player.invulnerableTime = 20;
            player.fallDistance = 0.0f;
            level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.0f, 1.3f);
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 16, 0.3, 0.3, 0.3, 0.05);
        } else {
            if (st.burstCooldown > 0) return;
            float cost = q == Quality.PERFECT ? BURST_COST_PERFECT : BURST_COST;
            if (st.steam < cost) {
                player.displayClientMessage(Component.translatable("message.clockworkdiver.no_steam", (int) st.steam, (int) cost), true);
                return;
            }
            st.steam -= cost;
            st.burstCooldown = 10;
            double radius = q == Quality.PERFECT ? 6.0 : 4.5;
            float damage = (q == Quality.PERFECT ? 12.0f : 8.0f) + progress.wrenchLevel * 2.0f;
            st.abilityDamage = true;
            try {
                for (Monster mob : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(radius))) {
                    if (mob.distanceTo(player) > radius) continue;
                    mob.hurt(player.damageSources().playerAttack(player), mob instanceof TimeEater ? damage * 0.6f : damage);
                    mob.knockback(1.3, player.getX() - mob.getX(), player.getZ() - mob.getZ());
                }
            } finally {
                st.abilityDamage = false;
            }
            for (int i = 0; i < 40; i++) {
                double a = i / 40.0 * Math.PI * 2.0;
                level.sendParticles(ParticleTypes.CLOUD, player.getX() + Math.cos(a) * 1.2, player.getY() + 0.4, player.getZ() + Math.sin(a) * 1.2,
                        0, Math.cos(a) * 0.6, 0.05, Math.sin(a) * 0.6, 1.0);
            }
            level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.6, 0.4, 0.6, 0.05);
            level.playSound(null, player.blockPosition(), ModSounds.STEAM_BURST.get(), SoundSource.PLAYERS, 1.2f, q == Quality.PERFECT ? 1.2f : 1.0f);
            if (q == Quality.PERFECT) {
                st.combo++;
            }
            DiveManager.onSteamBurst(player, radius, q);
        }
        sync(player, q);
    }
}
