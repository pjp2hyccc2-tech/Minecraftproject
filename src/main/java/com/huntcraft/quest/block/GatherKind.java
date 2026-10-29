package com.huntcraft.quest.block;

import com.huntcraft.quest.QuestRegistry;
import net.minecraft.world.level.block.Block;

import java.util.Locale;

/** The five gathering point types. */
public enum GatherKind {
    HERB, MUSHROOM, BUG, BONE, MINING;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Block block() {
        return switch (this) {
            case HERB -> QuestRegistry.GATHER_HERB.get();
            case MUSHROOM -> QuestRegistry.GATHER_MUSHROOM.get();
            case BUG -> QuestRegistry.GATHER_BUG.get();
            case BONE -> QuestRegistry.GATHER_BONE.get();
            case MINING -> QuestRegistry.GATHER_MINING.get();
        };
    }
}
