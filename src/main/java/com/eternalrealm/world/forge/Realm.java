package com.eternalrealm.world.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.AreaMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Where the game takes place: the アストリア dimension when it is installed, otherwise the overworld (development
 * worlds, the GameTest server). Areas are the rings of {@link AreaMap#standard()} around the centre, (0, 0) in アストリア
 * and the world spawn in the overworld; the capital at the centre is a safe zone without monster spawns.
 */
public final class Realm {
    public static final ResourceKey<Level> ASTORIA =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(ModInfo.MOD_ID, "astoria"));
    public static final AreaMap AREAS = AreaMap.standard();
    /** Radius of the capital (no monsters spawn inside). */
    public static final double SAFE_RADIUS = 96.0;
    /**
     * The royal capital's footprint (relative to the centre, a little larger than the build in
     * {@code data/eternalrealm/capital}): x in [-CAPITAL_HALF_X, CAPITAL_HALF_X], z in [CAPITAL_NORTH, CAPITAL_SOUTH].
     */
    public static final int CAPITAL_HALF_X = 186;
    public static final int CAPITAL_NORTH = -218;
    public static final int CAPITAL_SOUTH = 186;

    private Realm() {
    }

    public static ServerLevel level(MinecraftServer server) {
        ServerLevel astoria = server.getLevel(ASTORIA);
        return astoria != null ? astoria : server.overworld();
    }

    /** Whether the dedicated dimension exists (then the overworld is left alone). */
    public static boolean hasDimension(MinecraftServer server) {
        return server.getLevel(ASTORIA) != null;
    }

    public static boolean isRealm(Level level) {
        return level instanceof ServerLevel serverLevel && serverLevel == level(serverLevel.getServer());
    }

    /** Centre of the rings: the capital. */
    public static BlockPos center(ServerLevel level) {
        return level.dimension() == ASTORIA ? BlockPos.ZERO : level.getSharedSpawnPos();
    }

    public static AreaDefinition areaAt(ServerLevel level, double x, double z) {
        BlockPos center = center(level);
        return AREAS.areaAt(x - center.getX(), z - center.getZ());
    }

    /** Inside the capital: no monsters spawn, the city music plays, the buildings are protected. */
    public static boolean inCapital(ServerLevel level, double x, double z) {
        BlockPos center = center(level);
        double dx = x - center.getX();
        double dz = z - center.getZ();
        if (level.dimension() == ASTORIA) {
            return Math.abs(dx) <= CAPITAL_HALF_X && dz >= CAPITAL_NORTH && dz <= CAPITAL_SOUTH;
        }
        return dx * dx + dz * dz < SAFE_RADIUS * SAFE_RADIUS;
    }
}
