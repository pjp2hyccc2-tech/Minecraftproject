package jp.satsujinki.game;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Server-side index of running traps, so contestants can be scared of nearby machines cheaply. */
public final class TrapRegistry {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> RUNNING = new HashMap<>();

    private TrapRegistry() {
    }

    public static void set(Level level, BlockPos pos, boolean running) {
        if (level.isClientSide) return;
        Set<BlockPos> set = RUNNING.computeIfAbsent(level.dimension(), k -> new HashSet<>());
        if (running) set.add(pos.immutable());
        else set.remove(pos);
    }

    public static boolean isNearRunningTrap(Level level, BlockPos pos, int radius) {
        Set<BlockPos> set = RUNNING.get(level.dimension());
        if (set == null || set.isEmpty()) return false;
        int r2 = radius * radius;
        for (BlockPos p : set) {
            if (p.distSqr(pos) <= r2) return true;
        }
        return false;
    }

    public static void clear() {
        RUNNING.clear();
    }
}
