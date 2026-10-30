package com.nihonisshu.tabi.game;

import com.nihonisshu.tabi.data.Route;
import com.nihonisshu.tabi.entity.VehicleEntity;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Puts a waiting train/bus at a terminal whenever a player comes near and none is there. */
public final class TransitManager {
    private TransitManager() {}

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 7) return;
        ServerLevel level = server.getLevel(TabiWorldgen.JAPAN);
        if (level == null || level.players().isEmpty()) return;
        for (Route r : Route.all()) {
            for (Route.Station st : r.stations) {
                if (!st.terminal) continue;
                Vec3 c = r.pos(st.s);
                if (level.getNearestPlayer(c.x, c.y, c.z, 72, false) == null) continue;
                if (!level.isLoaded(BlockPos.containing(c))) continue;
                double reach = r.cars * r.carSpacing + 24;
                List<VehicleEntity> near = level.getEntitiesOfClass(VehicleEntity.class,
                        new AABB(c, c).inflate(reach, 12, reach), v -> v.routeId().equals(r.id));
                if (near.isEmpty()) {
                    VehicleEntity.spawnAt(level, r, st);
                }
            }
        }
    }
}
