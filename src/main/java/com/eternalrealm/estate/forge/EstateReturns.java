package com.eternalrealm.estate.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Where a player came from before entering an estate, kept in the player's own persistent data so it is saved
 * together with the player's position (the two can never disagree after a crash). Entering one estate from another
 * keeps the first return point.
 */
final class EstateReturns {
    static final String KEY = "eternalrealm_estate_return";

    record Point(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
    }

    private EstateReturns() {
    }

    /** Remembers where {@code player} stands now (unless they are inside the estates already). */
    static void remember(ServerPlayer player) {
        if (EstateLevels.isEstateLevel(player.level())) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        tag.putFloat("pitch", player.getXRot());
        player.getPersistentData().put(KEY, tag);
    }

    static Optional<Point> get(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(KEY)) {
            return Optional.empty();
        }
        CompoundTag tag = data.getCompound(KEY);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dim"));
        if (dimension == null) {
            return Optional.empty();
        }
        return Optional.of(new Point(ResourceKey.create(Registries.DIMENSION, dimension),
                new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")), tag.getFloat("yaw"),
                tag.getFloat("pitch")));
    }

    static void clear(ServerPlayer player) {
        player.getPersistentData().remove(KEY);
    }

    /** Whether a player can stand at {@code point} (its level exists, is not the estates or a dungeon, and there is
     * room). */
    static boolean usable(ServerPlayer player, Point point) {
        ServerLevel level = player.server.getLevel(point.dimension());
        if (level == null || EstateLevels.isEstateLevel(level)
                || com.eternalrealm.dungeon.forge.DungeonLevels.isDungeonLevel(level)) {
            return false; // a dungeon instance does not wait for anyone: its slot may be wiped or reused
        }
        BlockPos feet = BlockPos.containing(point.position());
        if (!level.isInWorldBounds(feet)) {
            return false;
        }
        level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
        return !level.getBlockState(feet).isSuffocating(level, feet)
                && !level.getBlockState(feet.above()).isSuffocating(level, feet.above());
    }
}
