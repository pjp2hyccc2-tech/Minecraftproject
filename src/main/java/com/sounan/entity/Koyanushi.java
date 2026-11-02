package com.sounan.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** 小屋主: sleeps in the empty huts. Wake it and it chases you out. */
public class Koyanushi extends SounanMonster {
    public static final int SLEEP = 0, WAKE = 1, CHASE = 2, FACE_WALL = 3, STAND = 4;

    public Koyanushi(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.36, 40);
    }
}
