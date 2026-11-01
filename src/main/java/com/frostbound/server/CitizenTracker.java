package com.frostbound.server;

import com.frostbound.config.FrostboundConfig;
import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingCategory;
import com.frostbound.core.city.City;
import com.frostbound.core.population.Citizen;
import com.frostbound.entity.CitizenEntity;
import com.frostbound.registry.ModRegistries;
import com.frostbound.world.Dims;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Level of detail for citizens: only the citizens nearest to players (within the configured radius, at most
 * the configured number per player) get an entity, placed where the simulation says they are (work during
 * the shift, home or the generator square otherwise, indoors at night). Runs once a second.
 */
public final class CitizenTracker {
    private static final int MAX_TOTAL = 200;
    private static final int MAX_SPAWNS_PER_RUN = 12;
    private static final Map<Integer, CitizenEntity> live = new HashMap<>();

    private record Wanted(Citizen citizen, BlockPos anchor, int wander, double dist2) {
    }

    private CitizenTracker() {
    }

    public static int liveCount() {
        return live.size();
    }

    public static void clear() {
        for (CitizenEntity e : live.values()) {
            if (!e.isRemoved()) {
                e.discard();
            }
        }
        live.clear();
    }

    public static void tick(MinecraftServer server, City city) {
        ServerLevel level = server.getLevel(Dims.FROZEN_WASTES);
        live.values().removeIf(Entity::isRemoved);
        if (level == null || !FrostboundConfig.NPC_ENABLED.get() || city == null) {
            clear();
            return;
        }
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) {
            clear();
            return;
        }
        int perPlayer = FrostboundConfig.NPC_PER_PLAYER.get();
        double radius = FrostboundConfig.NPC_RADIUS.get();
        int cap = Math.min(MAX_TOTAL, perPlayer * players.size());
        int hour = city.clock().hourOfDay();
        boolean shift = city.isShiftHour(hour);
        List<Wanted> wanted = new ArrayList<>();
        for (Citizen c : city.population().all()) {
            if (!c.present()) {
                continue;
            }
            Wanted w = anchor(city, c, hour, shift);
            if (w == null) {
                continue;
            }
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : players) {
                double dx = p.getX() - w.anchor().getX();
                double dz = p.getZ() - w.anchor().getZ();
                best = Math.min(best, dx * dx + dz * dz);
            }
            if (best <= radius * radius) {
                wanted.add(new Wanted(c, w.anchor(), w.wander(), best));
            }
        }
        wanted.sort(Comparator.comparingDouble(Wanted::dist2));
        if (wanted.size() > cap) {
            wanted = wanted.subList(0, cap);
        }
        Set<Integer> keep = new HashSet<>();
        for (Wanted w : wanted) {
            keep.add(w.citizen().id);
        }
        for (Iterator<Map.Entry<Integer, CitizenEntity>> it = live.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, CitizenEntity> e = it.next();
            if (!keep.contains(e.getKey())) {
                e.getValue().discard();
                it.remove();
            }
        }
        int spawned = 0;
        for (Wanted w : wanted) {
            Citizen c = w.citizen();
            CitizenEntity e = live.get(c.id);
            if (e == null) {
                if (spawned >= MAX_SPAWNS_PER_RUN || !level.isLoaded(w.anchor())) {
                    continue;
                }
                e = ModRegistries.CITIZEN.get().create(level);
                if (e == null) {
                    continue;
                }
                int ox = Math.floorMod(c.id * 7, 5) - 2;
                int oz = Math.floorMod(c.id * 13, 5) - 2;
                int x = w.anchor().getX() + ox;
                int z = w.anchor().getZ() + oz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                e.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
                e.citizenId = c.id;
                e.setLook(c.look);
                e.setCustomName(Component.literal(c.name));
                level.addFreshEntity(e);
                live.put(c.id, e);
                spawned++;
            }
            e.setAnchor(w.anchor(), w.wander());
            e.setMood(mood(c));
        }
    }

    private static int mood(Citizen c) {
        if (c.condition.sick()) {
            return 2;
        }
        if (c.tempBand() >= 2) {
            return 1;
        }
        return c.hungerBand() >= 2 ? 3 : 0;
    }

    private static Wanted anchor(City city, Citizen c, int hour, boolean shift) {
        boolean night = hour >= 22 || hour < 6;
        Building home = c.homeId >= 0 ? city.building(c.homeId) : null;
        Building work = c.workId >= 0 ? city.building(c.workId) : null;
        if (c.condition.sick()) {
            if (night) {
                return home == null ? square(city, c) : null;
            }
            Building medical = nearestMedical(city, home);
            if (medical != null) {
                return new Wanted(c, StructureBuilder.doorOutside(medical), 1, 0);
            }
            return home == null ? square(city, c) : new Wanted(c, StructureBuilder.doorOutside(home), 1, 0);
        }
        if (shift && work != null && (work.active() || work.state == Building.State.CONSTRUCTION) && !work.paused && !c.child()) {
            if (work.state == Building.State.CONSTRUCTION) {
                return new Wanted(c, new BlockPos((int) work.centerX(), work.y, work.maxZ() + 2), 3, 0);
            }
            return new Wanted(c, StructureBuilder.doorOutside(work), 2, 0);
        }
        if (night) {
            return home == null ? square(city, c) : null;
        }
        if (home == null || Math.floorMod(c.id + hour, 3) == 0) {
            return square(city, c);
        }
        return new Wanted(c, StructureBuilder.doorOutside(home), 2, 0);
    }

    private static Wanted square(City city, Citizen c) {
        double a = (c.id * 2.399963) % (Math.PI * 2);
        double r = 9 + Math.floorMod(c.id, 4);
        int x = city.generatorX() + (int) Math.round(Math.cos(a) * r);
        int z = city.generatorZ() + (int) Math.round(Math.sin(a) * r);
        return new Wanted(c, new BlockPos(x, city.generatorY(), z), 1, 0);
    }

    private static Building nearestMedical(City city, Building from) {
        Building best = null;
        double bestD = Double.MAX_VALUE;
        for (Building b : city.buildings()) {
            if (b.active() && b.type().category == BuildingCategory.MEDICAL) {
                double d = from == null ? 0 : Math.hypot(b.centerX() - from.centerX(), b.centerZ() - from.centerZ());
                if (d < bestD) {
                    bestD = d;
                    best = b;
                }
            }
        }
        return best;
    }
}
