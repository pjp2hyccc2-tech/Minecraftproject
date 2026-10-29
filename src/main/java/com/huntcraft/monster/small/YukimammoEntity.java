package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterDamage;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.ai.MonsterBrainGoal;
import com.huntcraft.entity.monster.ai.MonsterWanderGoal;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * ユキマンモ - shaggy snow mammoth with great curling tusks. Peaceful while grazing, but once hurt it trumpets,
 * then charges with lowered tusks, sweeps them sideways and stomps. The herd joins in.
 */
public class YukimammoEntity extends SmallHuntMonster {
    static final Hitzone HZ_HEAD = Hitzone.of(65, 70, 60, 30, 10, 20, 5, 10);
    static final Hitzone HZ_BODY = Hitzone.of(50, 55, 45, 30, 10, 20, 5, 5);
    static final Hitzone HZ_LEG = Hitzone.of(45, 45, 40, 25, 10, 15, 5, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("yukimammo", false)
            .health(50, 60).highRank(1.3F, 0.85F)
            .scale(0.95F, 1.05F)
            .weakness(Element.FIRE, 3).weakness(Element.THUNDER, 1).weakness(Element.DRAGON, 1)
            .part(new PartDef("head", 0, 1.3F, 1.25F, 0.8F, 0.8F, HZ_HEAD).ko())
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("leg_fl", -0.5F, 0, 0.55F, 0.45F, 0.8F, HZ_LEG))
            .part(new PartDef("leg_fr", 0.5F, 0, 0.55F, 0.45F, 0.8F, HZ_LEG))
            .rageThreshold(99F).flinchThreshold(0.3F)
            .carves(new RewardTable()
                    .low(HCItems.YUKIMAMMO_FUR, 45).low(HCItems.YUKIMAMMO_TUSK, 20).low(HCItems.RAW_MEAT, 35)
                    .high(HCItems.YUKIMAMMO_FUR, 42, 1, 2).high(HCItems.YUKIMAMMO_TUSK, 26).high(HCItems.RAW_MEAT, 32, 1, 2))
            .carveCount(2)
            .questReward(400, 10)
            .build();

    public static final String[] ANIMS = {"flinch", "tusk_charge", "tusk_sweep", "stomp", "graze", "trumpet", "shake"};

    public YukimammoEntity(EntityType<? extends YukimammoEntity> type, Level level) {
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
    public boolean isHostile() {
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MonsterBrainGoal(this));
        this.goalSelector.addGoal(6, new MonsterWanderGoal(this, 12));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new RetaliateGoal(this, 500));
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        MonsterAttack charge = TimelineAttack.builder("tusk_charge", 60).range(4.0F, 16).angle(25).weight(10).cooldown(120)
                .track(0, 16, 6)
                .sound(4, () -> SoundEvents.GOAT_SCREAMING_AMBIENT, 1.6F, 0.5F)
                .track(17, 46, 1.2F)
                .move(17, 46, 0.4, 0)
                .span(17, 46, (m, target, t) -> m.hitSphere(m.localPos(0, 0.7, 1.4), 1.2 * m.getMonsterScale(), 7, Element.NONE, 1.8F, MonsterDamage.FLAG_TRIP))
                .sound(18, HCSounds.STOMP, 1.0F, 1.0F).sound(26, HCSounds.STOMP, 1.0F, 1.0F).sound(34, HCSounds.STOMP, 1.0F, 1.0F).sound(42, HCSounds.STOMP, 1.0F, 1.0F)
                .particlesSpan(17, 46, ParticleTypes.SNOWFLAKE, 0, 0.2, -0.5, 2, 0.4)
                .move(47, 56, 0.12, 0)
                .build();
        MonsterAttack sweep = TimelineAttack.builder("tusk_sweep", 32).range(0, 3.2F).angle(70).weight(12).cooldown(30)
                .track(0, 8, 8)
                .sound(11, HCSounds.TAIL_SWING, 1.2F, 0.9F)
                .sweep(12, 18, 0.5F, 3.4F, -75, 75, 2.5F, 6, Element.NONE, 1.6F)
                .build();
        MonsterAttack stomp = TimelineAttack.builder("stomp", 44).range(0, 3.0F).angle(60).weight(8).cooldown(60)
                .track(0, 14, 5)
                .sound(8, () -> SoundEvents.GOAT_SCREAMING_AMBIENT, 1.4F, 0.6F)
                .hit(22, 0, 0.3, 1.6, 1.6F, 8, Element.NONE, 1.4F)
                .tremor(22, 4.0F)
                .particles(22, ParticleTypes.SNOWFLAKE, 0, 0.2, 1.6, 20, 0.8)
                .build();
        return List.of(charge, sweep, stomp);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean calm = getTarget() == null;
        boolean r = super.hurt(source, amount);
        if (r && calm && !level().isClientSide && !isDeadOrDying() && source.getEntity() instanceof LivingEntity && canAct()) {
            // angry trumpet before fighting back
            playAction("trumpet", 36);
            playSound(SoundEvents.GOAT_SCREAMING_AMBIENT, 2.0F, 0.55F);
        }
        return r;
    }

    @Override
    public float getTurnSpeed() {
        return 9.0F;
    }

    @Override
    public double getWalkSpeedModifier() {
        return 1.0;
    }

    @Override
    public double getRunSpeedModifier() {
        return 1.45;
    }

    @Override
    public int getAttackGap() {
        return 30;
    }

    @Override
    protected double runThreshold() {
        return 0.11;
    }

    @Override
    protected int flinchTicks() {
        return 18;
    }

    @Override
    protected int deathDuration() {
        return 36;
    }

    @Override
    protected IdleAction[] idleActions() {
        return new IdleAction[]{new IdleAction("graze", 80, 5), new IdleAction("trumpet", 36, 1), new IdleAction("shake", 30, 2)};
    }

    @Override
    protected void onIdleAction(String anim) {
        if ("trumpet".equals(anim)) playSound(SoundEvents.GOAT_SCREAMING_AMBIENT, 1.4F, 0.6F);
        else if ("shake".equals(anim)) burstParticles(ParticleTypes.SNOWFLAKE, position().add(0, 1.6, 0), 20, 0.8);
        else if ("graze".equals(anim)) playSound(SoundEvents.GENERIC_EAT, 0.6F, 0.6F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SNIFFER_IDLE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SNIFFER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SNIFFER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SNIFFER_STEP, 0.5F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + (this.getRandom().nextFloat() - 0.5F) * 0.1F;
    }
}
