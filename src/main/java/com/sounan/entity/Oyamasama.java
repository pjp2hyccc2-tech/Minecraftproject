package com.sounan.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** 御山様: the mountain itself. Hunts the final act; it hates fire. */
public class Oyamasama extends SounanMonster {
    public static final int STAND = 0, WALK = 1, CRAWL = 2, REACH = 3, LOOM = 4;

    public Oyamasama(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.3, 96);
    }
}
