package com.sounan.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** 写るもの: the lost, visible only through a camera. They stand facing the way out. */
public class Utsurumono extends SounanMonster {
    public static final int POINT = 0, STAND = 1, LOOK = 2;

    public Utsurumono(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.0, 16);
    }
}
