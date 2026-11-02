package com.sounan.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/** 覗き: peeks from behind cedar trunks. Provoked by approach, a steady beam or a flash photo. */
public class Nozoki extends SounanMonster {
    public static final int PEEK_LEFT = 0, PEEK_RIGHT = 1, STAND = 2, CRAWL = 3, RETREAT = 4;

    public Nozoki(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.3, 48);
    }
}
