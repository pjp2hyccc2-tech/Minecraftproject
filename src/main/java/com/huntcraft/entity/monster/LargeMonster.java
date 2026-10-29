package com.huntcraft.entity.monster;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Convenience base for large monsters. All behaviour lives in {@link HuntMonster}; the profile must be
 * built with {@code MonsterProfile.builder(id, true)}.
 */
public abstract class LargeMonster extends HuntMonster implements net.minecraft.world.entity.monster.Enemy {
    protected LargeMonster(EntityType<? extends LargeMonster> type, Level level) {
        super(type, level);
    }

    @Override
    public float getTurnSpeed() {
        return 5.5F;
    }
}
