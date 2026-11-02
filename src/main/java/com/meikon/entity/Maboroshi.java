package com.meikon.entity;

import com.meikon.game.GameManager;
import com.meikon.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 幻 — the bride as a hallucination: standing at the far end of a corridor, or right behind you when you turn
 * around. She disappears when you look straight at her or come close.
 */
public class Maboroshi extends Mob {
    public static final int MODE_FAR = 0;
    public static final int MODE_PEEK = 1;

    @Nullable private UUID viewer;
    private int mode;
    private int age;
    private int seen;
    private int maxAge = 20 * 12;

    public Maboroshi(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
        setSilent(true);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void setup(ServerPlayer player, int mode) {
        this.viewer = player.getUUID();
        this.mode = mode;
        this.maxAge = mode == MODE_PEEK ? 20 * 6 : 20 * 14;
        Vec3 d = player.getEyePosition().subtract(position());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        yRotO = yaw;
        yBodyRotO = yaw;
        yHeadRotO = yaw;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (!(level() instanceof ServerLevel level)) return;
        age++;
        ServerPlayer p = viewer == null ? null : (level.getPlayerByUUID(viewer) instanceof ServerPlayer sp ? sp : null);
        if (p == null || age > maxAge) {
            vanish(level, false);
            return;
        }
        Vec3 to = position().add(0, 1.6, 0).subtract(p.getEyePosition());
        double dist = to.length();
        double dot = p.getViewVector(1.0f).dot(to.normalize());
        boolean lineOfSight = p.hasLineOfSight(this);
        if (mode == MODE_PEEK) {
            if (dot > 0.55 && lineOfSight) {
                if (seen == 0) {
                    GameManager.peekSeen(p, this);
                }
                if (++seen > 7) vanish(level, false);
            }
        } else {
            if ((dot > 0.965 && lineOfSight) || dist < 6.5) seen++;
            if (seen > 9) vanish(level, true);
        }
    }

    private void vanish(ServerLevel level, boolean sound) {
        if (sound) {
            level.playSound(null, getX(), getY() + 1.5, getZ(), ModSounds.AMBIENT_WHISPER.get(), SoundSource.HOSTILE, 0.7f, 0.7f);
        }
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.2, getZ(), 12, 0.2, 0.6, 0.2, 0.01);
        discard();
    }
}
