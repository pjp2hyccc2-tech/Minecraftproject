package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.Hitzone;
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
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * ガメノス - huge, placid tortoise whose plated shell is overgrown with moss and flowers. It only defends itself:
 * a hurt Gamenos swings its heavy tail at whoever is behind it, or rears up and stomps on those in front.
 */
public class GamenosEntity extends SmallHuntMonster {
    static final Hitzone HZ_HEAD = Hitzone.of(75, 75, 70, 25, 10, 30, 20, 10);
    static final Hitzone HZ_SHELL = Hitzone.of(30, 40, 25, 15, 5, 20, 10, 5);
    static final Hitzone HZ_TAIL = Hitzone.of(55, 50, 50, 20, 10, 25, 15, 5);
    static final Hitzone HZ_LEG = Hitzone.of(60, 60, 55, 20, 10, 25, 15, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("gamenos", false)
            .health(45, 60).highRank(1.3F, 0.85F)
            .scale(0.95F, 1.05F)
            .weakness(Element.THUNDER, 3).weakness(Element.FIRE, 2).weakness(Element.ICE, 1)
            .part(new PartDef("head", 0, 0.7F, 1.45F, 0.6F, 0.55F, HZ_HEAD).ko())
            .part(PartDef.body(HZ_SHELL))
            .part(new PartDef("tail", 0, 0.25F, -1.45F, 0.5F, 0.4F, HZ_TAIL))
            .part(new PartDef("leg_fl", -0.7F, 0, 0.75F, 0.5F, 0.6F, HZ_LEG))
            .part(new PartDef("leg_fr", 0.7F, 0, 0.75F, 0.5F, 0.6F, HZ_LEG))
            .rageThreshold(99F).flinchThreshold(0.3F)
            .carves(new RewardTable()
                    .low(HCItems.GAMENOS_SHELL, 40).low(HCItems.RAW_MEAT, 35).low(HCItems.MONSTER_BONE_S, 25)
                    .high(HCItems.GAMENOS_SHELL, 44, 1, 2).high(HCItems.RAW_MEAT, 32, 1, 2).high(HCItems.MONSTER_BONE_S, 24))
            .carveCount(2)
            .questReward(300, 6)
            .build();

    public static final String[] ANIMS = {"flinch", "tail_swing", "stomp", "graze", "yawn"};

    public GamenosEntity(EntityType<? extends GamenosEntity> type, Level level) {
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
        this.goalSelector.addGoal(6, new MonsterWanderGoal(this, 10));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new RetaliateGoal(this, 400));
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        MonsterAttack tail = TimelineAttack.builder("tail_swing", 36).range(0, 3.6F).angle(180).weight(12).cooldown(30)
                .sound(11, HCSounds.TAIL_SWING, 1.4F, 0.7F)
                .sweep(12, 21, 0.5F, 3.8F, 100, 260, 2.0F, 6, Element.NONE, 1.6F)
                .build();
        MonsterAttack stomp = TimelineAttack.builder("stomp", 40).range(0, 2.8F).angle(55).weight(10).cooldown(40)
                .track(0, 12, 4)
                .hit(21, 0, 0.3, 1.5, 1.5F, 6, Element.NONE, 1.3F)
                .tremor(21, 3.0F)
                .particles(21, ParticleTypes.CAMPFIRE_COSY_SMOKE, 0, 0.1, 1.5, 12, 0.6)
                .sound(20, () -> SoundEvents.TURTLE_AMBIENT_LAND, 1.4F, 0.5F)
                .build();
        return List.of(tail, stomp);
    }

    @Override
    public float getTurnSpeed() {
        return 7.0F;
    }

    @Override
    public double getWalkSpeedModifier() {
        return 1.0;
    }

    @Override
    public double getRunSpeedModifier() {
        return 1.4;
    }

    @Override
    public int getAttackGap() {
        return 34;
    }

    @Override
    protected double runThreshold() {
        return 0.085;
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
        return new IdleAction[]{new IdleAction("graze", 90, 5), new IdleAction("yawn", 50, 2)};
    }

    @Override
    protected void onIdleAction(String anim) {
        if ("graze".equals(anim)) playSound(SoundEvents.GENERIC_EAT, 0.6F, 0.6F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.TURTLE_AMBIENT_LAND;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TURTLE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.TURTLE_SHAMBLE, 0.6F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return 0.55F + (this.getRandom().nextFloat() - 0.5F) * 0.1F;
    }
}
