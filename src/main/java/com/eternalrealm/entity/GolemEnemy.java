package com.eternalrealm.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** {@code eternalrealm:golem_enemy}: treants and stone, sand, ice and lava golems, ruin constructs. */
public class GolemEnemy extends ERMonster {
    public GolemEnemy(EntityType<? extends GolemEnemy> type, Level level) {
        super(type, level);
    }

    @Override
    public EnemyRig rig() {
        return EnemyRig.GOLEM;
    }
}
