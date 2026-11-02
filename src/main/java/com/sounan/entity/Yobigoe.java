package com.sounan.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** 呼び声: a figure with a headlamp that calls out like a rescuer. Walk toward it and it comes for you. */
public class Yobigoe extends SounanMonster {
    public static final int CALL = 0, IDLE = 1, TURN = 2, RUSH = 3;

    public Yobigoe(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.36, 64);
    }
}
