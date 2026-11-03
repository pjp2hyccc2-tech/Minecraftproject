package com.hitori.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Noises the doll can hear (docs/DESIGN.md §3.3): footsteps by surface (tatami 3, flooring 5, tiles 6, sprinting 12),
 * the closet's fusuma (6), peeking (2), doors (8), a dropped item (6), a ritual line spoken (10), frightened breathing
 * (4, with salt water in the mouth 2), a gasp (8), the phone vibrating (7). Each noise is heard once by the doll
 * ({@link #since}); the same source making the same noise twice within a few ticks counts once (the world and the
 * game may both report a door).
 */
public final class NoiseSystem {
    public record Noise(long id, Vec3 pos, float radius, String cause, @Nullable UUID source, float weight, long time) {
        @Nullable
        public ServerPlayer player(ServerLevel level) {
            return GameManager.player(level, source);
        }
    }

    private static final List<Noise> RECENT = new ArrayList<>();
    private static long nextId = 1;

    private NoiseSystem() {}

    /** How strongly a cause carries. */
    public static float weight(String cause) {
        return switch (cause) {
            case "gasp", "phone", "speak", "drop", "dish" -> 1.4f;
            case "sprint", "door", "fusuma", "cup" -> 1.15f;
            case "breath" -> 0.7f;
            case "peek", "rustle" -> 0.8f;
            default -> 1.0f;
        };
    }

    public static synchronized void emit(ServerLevel level, Vec3 pos, float radius, String cause, @Nullable Entity source) {
        if (radius <= 0.0f || !GameManager.isGameLevel(level)) return;
        long now = level.getGameTime();
        RECENT.removeIf(n -> now - n.time() > 60);
        UUID src = source instanceof Player ? source.getUUID() : null;
        for (int i = RECENT.size() - 1; i >= 0; i--) {
            Noise n = RECENT.get(i);
            if (now - n.time() > 6) break;
            if (n.cause().equals(cause) && java.util.Objects.equals(n.source(), src) && n.pos().distanceToSqr(pos) < 2.25) {
                if (radius > n.radius()) RECENT.set(i, new Noise(n.id(), n.pos(), radius, cause, src, n.weight(), n.time()));
                return;
            }
        }
        RECENT.add(new Noise(nextId++, pos, radius, cause, src, weight(cause), now));
        if (RECENT.size() > 96) RECENT.remove(0);
    }

    /** Noises newer than {@code lastId}. */
    public static synchronized List<Noise> since(long lastId) {
        if (RECENT.isEmpty() || RECENT.get(RECENT.size() - 1).id() <= lastId) return List.of();
        List<Noise> out = new ArrayList<>();
        for (Noise n : RECENT) if (n.id() > lastId) out.add(n);
        return out;
    }

    public static synchronized long lastId() {
        return nextId - 1;
    }

    public static synchronized List<Noise> recent() {
        return new ArrayList<>(RECENT);
    }

    public static synchronized void clear() {
        RECENT.clear();
    }
}
