package com.eternalrealm.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** {@code eternalrealm:beast_enemy}: wolves, hounds, rats, salamanders and scorpions. */
public class BeastEnemy extends ERMonster {
    public BeastEnemy(EntityType<? extends BeastEnemy> type, Level level) {
        super(type, level);
    }

    @Override
    public EnemyRig rig() {
        return EnemyRig.BEAST;
    }
}
