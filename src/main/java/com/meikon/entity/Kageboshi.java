package com.meikon.entity;

import com.meikon.game.GameManager;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 影法師 — a shadow that lives where no light reaches. It drifts toward you while your lantern is dark; shine
 * the lantern at it and it tears apart. If it touches you it drinks your light (the lantern goes out).
 */
public class Kageboshi extends Monster {
    private int life = 20 * 70;

    public Kageboshi(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {
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

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        if (--life <= 0 || level.getMaxLocalRawBrightness(blockPosition().above()) >= 9) {
            dissolve(level);
            return;
        }
        ServerPlayer target = GameManager.nearestHuntable(level, position(), 28.0);
        if (target == null) {
            getNavigation().stop();
            return;
        }
        getLookControl().setLookAt(target, 10f, 10f);
        if (GameManager.lanternBurns(target, this)) {
            GameManager.addFear(target, 2.0f);
            dissolve(level);
            return;
        }
        double speed = GameManager.lanternLit(target) ? 0.55 : 0.9;
        double dist = distanceTo(target);
        if (dist < 3.0 && hasLineOfSight(target)) {
            // the path ends one block short of the player: glide the last steps directly
            getNavigation().stop();
            getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
        } else if (tickCount % 10 == 0) {
            getNavigation().moveTo(target, speed);
        }
        if (dist < 1.35) {
            GameManager.shadowTouch(target, this);
            dissolve(level);
        }
    }

    public void dissolve(ServerLevel level) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.0, getZ(), 30, 0.3, 0.6, 0.3, 0.02);
        level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.4, getZ(), 4, 0.2, 0.2, 0.2, 0.02);
        playSound(ModSounds.SHADOW_WHOOSH.get(), 1.0f, 0.8f + random.nextFloat() * 0.3f);
        discard();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return distance > 64 * 64;
    }
}
