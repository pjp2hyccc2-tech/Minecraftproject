package com.sounan.game;

import com.sounan.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Noises in the mansion that the bride can hear (footsteps, doors, drawers, marbles, breathing...). */
public final class NoiseSystem {
    public enum Source { STEP, SPRINT, DOOR, DRAWER, MARBLE, BELL, VOICE, FALL, OTHER }

    public record Noise(Vec3 pos, float radius, long time, Source source, double strength) {}

    private static final List<Noise> NOISES = new ArrayList<>();
    private static final int MEMORY = 40;

    private NoiseSystem() {}

    public static void emit(ServerLevel level, BlockPos pos, float radius, Source source) {
        emit(level, Vec3.atCenterOf(pos), radius, source);
    }

    public static synchronized void emit(ServerLevel level, Vec3 pos, float radius, Source source) {
        if (!ModDimensions.isKakuriyo(level)) return;
        long now = level.getGameTime();
        NOISES.removeIf(n -> now - n.time() > MEMORY);
        NOISES.add(new Noise(pos, radius, now, source, 1.0));
        if (NOISES.size() > 64) NOISES.remove(0);
    }

    /** The most attention-grabbing recent noise this listener can hear, or null. */
    @Nullable
    public static synchronized Noise loudest(ServerLevel level, Vec3 listener, double hearing) {
        long now = level.getGameTime();
        Noise best = null;
        double bestScore = 0.0;
        for (Noise n : NOISES) {
            long age = now - n.time();
            if (age > MEMORY) continue;
            double r = n.radius() * hearing;
            double d = n.pos().distanceTo(listener);
            if (d > r) continue;
            double score = (r - d) / r + 0.3 - age * 0.006;
            if (score > bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best == null ? null : new Noise(best.pos(), best.radius(), best.time(), best.source(), bestScore);
    }

    public static synchronized void clear() {
        NOISES.clear();
    }
}
