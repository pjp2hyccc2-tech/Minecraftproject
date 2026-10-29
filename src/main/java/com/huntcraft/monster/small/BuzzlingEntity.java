package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * バズリング - giant wasp-beetle that hovers on blurring membrane wings. Fires poison stingers from range and
 * dives at its prey. Knocked to the ground by stuns / flash, it crawls until it can take off again.
 */
public class BuzzlingEntity extends SmallHuntMonster {
    static final Hitzone HZ_HEAD = Hitzone.of(80, 80, 80, 30, 10, 20, 25, 10);
    static final Hitzone HZ_BODY = Hitzone.of(65, 70, 70, 30, 10, 20, 25, 5);
    static final Hitzone HZ_ABDOMEN = Hitzone.of(75, 70, 80, 35, 10, 20, 25, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("buzzling", false)
            .health(10, 18).highRank(1.35F, 0.85F)
            .scale(0.9F, 1.1F)
            .weakness(Element.FIRE, 3).weakness(Element.ICE, 2).weakness(Element.THUNDER, 1)
            .part(new PartDef("head", 0, 0.3F, 0.45F, 0.4F, 0.4F, HZ_HEAD))
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("abdomen", 0, 0.1F, -0.55F, 0.45F, 0.45F, HZ_ABDOMEN))
            .rageThreshold(99F).flinchThreshold(0.15F)
            .carves(new RewardTable()
                    .low(HCItems.BUZZLING_WING, 45).low(HCItems.BUZZLING_STINGER, 30).low(HCItems.POISON_SAC, 25)
                    .high(HCItems.BUZZLING_WING, 40, 1, 2).high(HCItems.BUZZLING_STINGER, 34).high(HCItems.POISON_SAC, 26))
            .carveCount(1)
            .questReward(300, 8)
            .build();

    public static final String[] ANIMS = {"flinch", "stinger_shot", "dive_sting"};

    private int groundTicks;
    private int strafeDir;
    private int strafeTimer;

    public BuzzlingEntity(EntityType<? extends BuzzlingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public MonsterProfile profile() {
        return PROFILE;
    }

    @Override
    public String[] animationNames() {
        return ANIMS;
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        ProjectileSpec stinger = ProjectileSpec.of(MonsterProjectile.Kind.SPIKE, 3, Element.POISON).speed(1.15F).size(0.45F).lifetime(40);
        MonsterAttack shot = TimelineAttack.builder("stinger_shot", 30).range(2.5F, 14).angle(40).weight(12).cooldown(45).airOnly()
                .track(0, 14, 10)
                .span(0, 29, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().scale(0.7)))
                .projectile(14, 0, 0.05, 0.4, stinger)
                .sound(13, () -> SoundEvents.LLAMA_SPIT, 1.0F, 1.6F)
                .build();
        MonsterAttack dive = TimelineAttack.builder("dive_sting", 40).range(0.5F, 9).angle(60).weight(10).cooldown(70).airOnly()
                .track(0, 10, 14)
                .span(0, 9, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().scale(0.6).add(0, 0.035, 0)))
                .sound(9, () -> SoundEvents.BEE_LOOP_AGGRESSIVE, 1.2F, 1.3F)
                .span(11, 24, (m, target, t) -> {
                    if (target != null) {
                        Vec3 d = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(m.position());
                        double len = Math.max(0.1, d.length());
                        m.setDeltaMovement(d.scale(Math.min(0.75, len * 0.4) / len));
                        m.turnTowards(target.position(), 20);
                    }
                })
                .hitSpan(11, 26, 0, 0.0, 0.35, 0.75F, 4, Element.POISON, 0.6F)
                .sound(16, () -> SoundEvents.BEE_STING, 1.0F, 0.8F)
                .span(27, 39, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().scale(0.7).add(0, 0.045, 0)))
                .build();
        return List.of(shot, dive);
    }

    // ------------------------------------------------------------------ hovering
    @Override
    protected void smallTick() {
        if (!isFlying()) {
            // grounded (stun / flash / sleep): crawl, then buzz back into the air
            if (!isLocked() && !isActing()) {
                if (++groundTicks > 30) {
                    setFlying(true);
                    setDeltaMovement(getDeltaMovement().add(0, 0.3, 0));
                    groundTicks = 0;
                }
            }
            return;
        }
        groundTicks = 0;
        if (isActing()) return;
        // keep a hover height of ~1.6-3 blocks above the ground
        double ground = groundBelow(position(), 10);
        double want = ground + (getTarget() != null ? 2.4 : 1.8);
        double dy = want - getY();
        if (Math.abs(dy) > 0.25) setDeltaMovement(getDeltaMovement().add(0, Mth.clamp(dy * 0.03, -0.04, 0.04), 0));
        if (getTarget() == null && this.getNavigation().isDone() && this.tickCount % 3 == 0) {
            setDeltaMovement(getDeltaMovement().scale(0.85));
        }
    }

    @Override
    public void flyTowards(Vec3 pos, double speed) {
        // never climb high like a wyvern: clamp the destination to a low hover above the ground
        double ground = groundBelow(new Vec3(pos.x, Math.max(pos.y, getY()) + 2, pos.z), 16);
        double y = Mth.clamp(pos.y, ground + 1.2, ground + 3.0);
        super.flyTowards(new Vec3(pos.x, y, pos.z), Math.min(speed, 0.3));
    }

    @Override
    public void combatMove(LivingEntity target, double dist, float angle, boolean waiting) {
        if (!isFlying()) {
            super.combatMove(target, dist, angle, waiting);
            return;
        }
        // orbit the target at medium range
        if (--strafeTimer <= 0) {
            strafeDir = getRandom().nextBoolean() ? 1 : -1;
            strafeTimer = 30 + getRandom().nextInt(40);
        }
        Vec3 rel = position().subtract(target.position());
        rel = new Vec3(rel.x, 0, rel.z);
        if (rel.lengthSqr() < 1.0E-3) rel = new Vec3(1, 0, 0);
        Vec3 dir = rel.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(strafeDir);
        double want = waiting ? 5.0 : 4.0;
        Vec3 goal = target.position().add(dir.scale(want)).add(side.scale(2.2)).add(0, 2.0 + target.getBbHeight() * 0.4, 0);
        Vec3 d = goal.subtract(position());
        double len = d.length();
        Vec3 v = len > 0.05 ? d.scale(Math.min(0.26, len) / len) : Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().scale(0.8).add(v.scale(0.2)));
        turnTowards(target.position(), getTurnSpeed());
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        setFlying(true);
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public int getAttackGap() {
        return isTired() ? 34 : 20;
    }

    @Override
    protected boolean wantsIdleActions() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEE_LOOP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BEE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEE_DEATH;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 60;
    }

    @Override
    public float getVoicePitch() {
        return 0.65F + (this.getRandom().nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        groundTicks = 25;
    }
}
