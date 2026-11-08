package com.eternalrealm.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomFlyingGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;

/**
 * {@code eternalrealm:flyer_enemy}: harpies, wraiths, spirits and sentinels. Hovers (no gravity) and moves with a
 * flying path navigation; in combat it stays a little above its target.
 */
public class FlyerEnemy extends ERMonster {
    public FlyerEnemy(EntityType<? extends FlyerEnemy> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    @Override
    public EnemyRig rig() {
        return EnemyRig.FLYER;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        navigation.setCanPassDoors(true);
        return navigation;
    }

    @Override
    protected Goal wanderGoal() {
        return new WaterAvoidingRandomFlyingGoal(this, 0.8);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }
}
