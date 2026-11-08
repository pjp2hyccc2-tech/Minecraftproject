package com.eternalrealm.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** {@code eternalrealm:humanoid_enemy}: bandits, undead, cultists, knights, mages, imps, shades... */
public class HumanoidEnemy extends ERMonster {
    public HumanoidEnemy(EntityType<? extends HumanoidEnemy> type, Level level) {
        super(type, level);
    }

    @Override
    public EnemyRig rig() {
        return EnemyRig.HUMANOID;
    }
}
