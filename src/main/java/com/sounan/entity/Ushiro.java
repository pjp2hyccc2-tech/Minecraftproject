package com.sounan.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** 後ろ: walks right behind you. Turn around and it is face to face with you. */
public class Ushiro extends SounanMonster {
    public static final int LEAN = 0, GRAB = 1;

    public Ushiro(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.3, 32);
    }
}
