package com.eternalrealm.estate.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstatePlot;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/**
 * Where estates are built: the void dimension {@code eternalrealm:estates} (data/eternalrealm/dimension: no weather,
 * no mobs, a fixed late-afternoon sun). A plot's blueprint origin (local 0/0/0, the floor players stand on is local
 * y = 0) is the plot's centre at y = {@value #BASE_Y}.
 */
public final class EstateLevels {
    public static final ResourceKey<Level> ESTATES =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ModInfo.MOD_ID, "estates"));
    /** World y of a blueprint's local y = 0 (high above the void, so the sky looks the same as anywhere). */
    public static final int BASE_Y = 100;

    private EstateLevels() {
    }

    public static Optional<ServerLevel> level(MinecraftServer server) {
        return Optional.ofNullable(server.getLevel(ESTATES));
    }

    public static boolean isEstateLevel(Level level) {
        return level != null && level.dimension() == ESTATES;
    }

    /** World position of the plot's blueprint origin. */
    public static BlockPos origin(EstatePlot plot) {
        return new BlockPos(plot.originX(), BASE_Y, plot.originZ());
    }

    /** World position of a local blueprint position on {@code plot}. */
    public static BlockPos at(EstatePlot plot, BlockPos local) {
        return origin(plot).offset(local.getX(), local.getY(), local.getZ());
    }

    /**
     * The box a build of {@code blueprint} on {@code plot} can occupy (its tiles, from its lowest to its highest
     * layer, within the dimension). Without a blueprint: the largest box a blueprint can have.
     */
    public static AABB box(EstatePlot plot, Blueprint blueprint, ServerLevel level) {
        BlockPos origin = origin(plot);
        int[] bounds = blueprint != null ? blueprint.tileBounds() : new int[]{-192, -224, 191, 191};
        int minY = blueprint != null ? blueprint.minY() : -32;
        int maxY = blueprint != null ? blueprint.maxY() : 159;
        double y0 = Math.max(level.getMinBuildHeight(), origin.getY() + minY);
        double y1 = Math.min(level.getMaxBuildHeight(), origin.getY() + maxY + 1);
        return new AABB(origin.getX() + bounds[0], y0, origin.getZ() + bounds[1], origin.getX() + bounds[2] + 1, y1,
                origin.getZ() + bounds[3] + 1);
    }
}
