package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.registry.HCItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * グラシカ - gentle spotted deer with branching antlers. Never attacks: it grazes, keeps its distance from
 * hunters and the whole herd bolts when one of them is hurt.
 */
public class GrasicaEntity extends SmallHuntMonster {
    static final Hitzone HZ_HEAD = Hitzone.of(90, 90, 85, 25, 25, 25, 20, 15);
    static final Hitzone HZ_BODY = Hitzone.of(80, 80, 80, 25, 25, 25, 20, 10);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("grasica", false)
            .health(12, 20).highRank(1.0F, 0.9F)
            .scale(0.9F, 1.1F)
            .weakness(Element.FIRE, 2).weakness(Element.THUNDER, 2).weakness(Element.ICE, 2).weakness(Element.WATER, 2)
            .part(new PartDef("head", 0, 1.15F, 0.7F, 0.45F, 0.45F, HZ_HEAD).ko())
            .part(PartDef.body(HZ_BODY))
            .rageThreshold(99F).flinchThreshold(0.2F)
            .carves(new RewardTable()
                    .low(HCItems.RAW_MEAT, 65).low(HCItems.GRASICA_ANTLER, 35)
                    .high(HCItems.RAW_MEAT, 60, 1, 2).high(HCItems.GRASICA_ANTLER, 40))
            .carveCount(1)
            .questReward(150, 4)
            .build();

    public static final String[] ANIMS = {"flinch", "graze", "alert", "scratch"};

    public GrasicaEntity(EntityType<? extends GrasicaEntity> type, Level level) {
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
        super.registerGoals();
        // skittish: walks away from hunters that come close (sneaking hunters are not noticed)
        this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 5.0F, 1.0, 1.5,
                e -> e instanceof Player p && !p.isCrouching() && !p.isCreative() && !p.isSpectator()));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker) {
            // the whole herd panics
            for (GrasicaEntity g : level().getEntitiesOfClass(GrasicaEntity.class, getBoundingBox().inflate(14), e -> e != this && e.isAlive())) {
                g.setLastHurtByMob(attacker);
            }
        }
        return r;
    }

    @Override
    protected void smallTick() {
        // forget the hunter once the panic is over (core sets a target on every hit)
        if (getTarget() != null && getLastHurtByMob() == null) setTarget(null);
    }

    @Override
    protected double runThreshold() {
        return 0.16;
    }

    @Override
    protected IdleAction[] idleActions() {
        return new IdleAction[]{new IdleAction("graze", 70, 5), new IdleAction("alert", 40, 2), new IdleAction("scratch", 30, 1)};
    }

    @Override
    protected void onIdleAction(String anim) {
        if ("graze".equals(anim)) playSound(SoundEvents.GOAT_EAT, 0.6F, 1.3F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.GOAT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GOAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GOAT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.35F + (this.getRandom().nextFloat() - 0.5F) * 0.15F;
    }
}
