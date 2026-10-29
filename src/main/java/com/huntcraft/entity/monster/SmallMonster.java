package com.huntcraft.entity.monster;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Convenience base for small monsters. Hostile ones keep the default brain; herbivores override
 * {@link #isHostile()} to return false and get a panic goal instead.
 */
public abstract class SmallMonster extends HuntMonster {
    protected SmallMonster(EntityType<? extends SmallMonster> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        if (!isHostile()) {
            this.goalSelector.addGoal(1, new PanicGoal(this, 1.6));
            this.goalSelector.addGoal(6, new com.huntcraft.entity.monster.ai.MonsterWanderGoal(this, 10));
            this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        } else {
            super.registerGoals();
            this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        }
    }
}
