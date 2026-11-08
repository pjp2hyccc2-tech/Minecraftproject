package com.eternalrealm.dungeon.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.InstanceSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Where instances are built: the void dimension {@code eternalrealm:dungeons} (data/eternalrealm/dimension), slot
 * {@code i} at x = i × 1024, floors at y = {@value #BASE_Y}. Should the dimension be missing, instances go high above a
 * far corner of the overworld.
 */
public final class DungeonLevels {
    public static final ResourceKey<Level> DUNGEONS =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ModInfo.MOD_ID, "dungeons"));
    public static final int BASE_Y = 64;
    private static final int FALLBACK_OFFSET = 4_000_000;
    private static final int FALLBACK_Y = 290;

    private DungeonLevels() {
    }

    public static ServerLevel level(MinecraftServer server) {
        ServerLevel dungeons = server.getLevel(DUNGEONS);
        return dungeons != null ? dungeons : server.overworld();
    }

    public static boolean isDungeonLevel(Level level) {
        return level.dimension() == DUNGEONS;
    }

    /** World position of the slot's layout origin. */
    public static BlockPos origin(ServerLevel level, InstanceSlot slot) {
        if (isDungeonLevel(level)) {
            return new BlockPos(slot.originX(), BASE_Y, slot.originZ());
        }
        return new BlockPos(FALLBACK_OFFSET + slot.originX(), FALLBACK_Y, FALLBACK_OFFSET + slot.originZ());
    }

    public static BlockPos at(BlockPos origin, BlockPoint point) {
        return origin.offset(point.x(), point.y(), point.z());
    }
}
