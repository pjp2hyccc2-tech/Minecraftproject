package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterDamage;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * ボアファング - stubborn tusked boar. Paws the ground, then charges in a straight line (dazed if it slams
 * into a wall); tosses hunters who get too close with an upward tusk swing.
 */
public class BoarfangEntity extends SmallHuntMonster {
    static final Hitzone HZ_HEAD = Hitzone.of(70, 75, 60, 25, 10, 20, 25, 10);
    static final Hitzone HZ_BODY = Hitzone.of(55, 55, 50, 20, 10, 15, 20, 5);
    static final Hitzone HZ_REAR = Hitzone.of(65, 60, 60, 20, 10, 15, 20, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("boarfang", false)
            .health(30, 48).highRank(1.35F, 0.85F)
            .scale(0.9F, 1.12F)
            .weakness(Element.FIRE, 2).weakness(Element.ICE, 2).weakness(Element.THUNDER, 1)
            .part(new PartDef("head", 0, 0.35F, 0.85F, 0.6F, 0.55F, HZ_HEAD).ko())
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("rear", 0, 0.35F, -0.6F, 0.6F, 0.6F, HZ_REAR))
            .rageThreshold(99F).flinchThreshold(0.15F)
            .carves(new RewardTable()
                    .low(HCItems.BOARFANG_HIDE, 40).low(HCItems.BOARFANG_TUSK, 25).low(HCItems.RAW_MEAT, 35)
                    .high(HCItems.BOARFANG_HIDE, 38, 1, 2).high(HCItems.BOARFANG_TUSK, 30).high(HCItems.RAW_MEAT, 32))
            .carveCount(1)
            .questReward(350, 8)
            .build();

    public static final String[] ANIMS = {"flinch", "charge", "tusk_toss", "root", "shake"};

    public BoarfangEntity(EntityType<? extends BoarfangEntity> type, Level level) {
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
        MonsterAttack charge = TimelineAttack.builder("charge", 56).range(3.5F, 16).angle(25).weight(12).cooldown(90)
                .track(0, 16, 10)
                .sound(2, () -> SoundEvents.HOGLIN_ANGRY, 1.3F, 1.2F)
                .particlesSpan(4, 15, ParticleTypes.CAMPFIRE_COSY_SMOKE, 0, 0.1, -0.4, 1, 0.15)
                .track(17, 42, 1.5F)
                .move(17, 42, 0.46, 0)
                .span(17, 42, (m, target, t) -> {
                    m.hitSphere(m.localPos(0, 0.45, 0.85), 0.95 * m.getMonsterScale(), 5, Element.NONE, 1.6F, MonsterDamage.FLAG_TRIP);
                    if (m.horizontalCollision && t > 19) {
                        // slammed into a wall: dazed for a moment
                        m.playSound(HCSounds.HIT_HEAVY.get(), 1.2F, 0.8F);
                        m.burstParticles(ParticleTypes.CRIT, m.localPos(0, 0.6, 0.9), 12, 0.3);
                        m.lock(HuntMonster.FLAG_STUNNED, 50);
                    }
                })
                .sound(17, HCSounds.STOMP, 0.8F, 1.4F).sound(25, HCSounds.STOMP, 0.8F, 1.4F).sound(33, HCSounds.STOMP, 0.8F, 1.4F)
                .move(43, 52, 0.15, 0)
                .particlesSpan(43, 50, ParticleTypes.CAMPFIRE_COSY_SMOKE, 0, 0.1, 0.4, 1, 0.2)
                .build();
        MonsterAttack toss = TimelineAttack.builder("tusk_toss", 24).range(0, 2.4F).angle(40).weight(12).cooldown(20)
                .track(0, 8, 16)
                .move(8, 10, 0.18, 0)
                .hitFlags(10, 0, 0.5, 1.0, 0.95F, 4, Element.NONE, 1.2F, MonsterDamage.FLAG_LAUNCH)
                .sound(9, () -> SoundEvents.HOGLIN_ATTACK, 1.2F, 1.15F)
                .build();
        return List.of(charge, toss);
    }

    @Override
    public void combatMove(LivingEntity target, double dist, float angle, boolean waiting) {
        float s = getMonsterScale();
        if (waiting && dist < 2.5 * s) {
            // back off to make room for the next charge
            if (this.tickCount % 10 == 0) {
                net.minecraft.world.phys.Vec3 away = position().subtract(target.position()).normalize().scale(4.0).add(position());
                this.getNavigation().moveTo(away.x, away.y, away.z, getWalkSpeedModifier());
            }
            return;
        }
        super.combatMove(target, dist, angle, waiting);
    }

    @Override
    public int getAttackGap() {
        return isTired() ? 40 : 22;
    }

    @Override
    protected IdleAction[] idleActions() {
        return new IdleAction[]{new IdleAction("root", 50, 3), new IdleAction("shake", 24, 1)};
    }

    @Override
    protected void onIdleAction(String anim) {
        if ("root".equals(anim)) playSound(SoundEvents.HOGLIN_AMBIENT, 0.7F, 1.4F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HOGLIN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HOGLIN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HOGLIN_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.HOGLIN_STEP, 0.3F, 1.3F);
    }

    @Override
    public float getVoicePitch() {
        return 1.25F + (this.getRandom().nextFloat() - 0.5F) * 0.15F;
    }
}
