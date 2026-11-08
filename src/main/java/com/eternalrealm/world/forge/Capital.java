package com.eternalrealm.world.forge;

import com.eternalrealm.world.forge.capital.CapitalBlueprint;
import com.eternalrealm.world.forge.capital.CapitalBuilder;
import com.eternalrealm.world.gen.HubSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The capital of アストリア: the blueprint capital shipped in {@code data/eternalrealm/capital} when the mod has one
 * (built progressively by {@link CapitalBuilder}, replacing the classic town in existing worlds), otherwise the
 * classic walled town of {@link HubBuilder}. Server thread only.
 */
public final class Capital {
    private static MinecraftServer cachedFor;
    private static Optional<CapitalBlueprint> cached = Optional.empty();

    private Capital() {
    }

    /** The blueprint of this server's data (loaded once per server). */
    public static Optional<CapitalBlueprint> blueprint(MinecraftServer server) {
        if (cachedFor != server) {
            cachedFor = server;
            cached = CapitalBlueprint.load(server.getResourceManager());
        }
        return cached;
    }

    /**
     * The capital's layout. With a blueprint this starts building it if the world does not have it yet (the layout is
     * valid at once; the city appears around it over the next minutes), otherwise it builds the classic town now.
     */
    public static HubLayout layout(ServerLevel astoria) {
        Optional<CapitalBlueprint> blueprint = blueprint(astoria.getServer());
        if (blueprint.isEmpty()) {
            return HubBuilder.ensureBuilt(astoria);
        }
        int groundY = HubLayout.groundY(astoria);
        HubLayout layout = layout(groundY, blueprint.get());
        if (!(astoria.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) {
            CapitalBuilder.ensureStarted(astoria, blueprint.get(), groundY, layout.spawnPoint());
        }
        return layout;
    }

    /** Whether the capital stands completely (the classic town, or the current blueprint fully built). */
    public static boolean ready(ServerLevel astoria) {
        Optional<CapitalBlueprint> blueprint = blueprint(astoria.getServer());
        if (blueprint.isEmpty()) {
            return HubBuilder.isBuilt(astoria);
        }
        return blueprint.get().build().equals(AstoriaWorldState.get(astoria).capitalBuild());
    }

    static HubLayout layout(int groundY, CapitalBlueprint blueprint) {
        Map<HubSpot, BlockPos> spots = new EnumMap<>(HubSpot.class);
        Map<HubSpot, Float> yaws = new EnumMap<>(HubSpot.class);
        Map<String, BlockPos> named = new HashMap<>();
        Map<String, Float> namedYaws = new HashMap<>();
        for (Map.Entry<String, CapitalBlueprint.Spot> entry : blueprint.spots().entrySet()) {
            named.put(entry.getKey(), entry.getValue().pos());
            namedYaws.put(entry.getKey(), entry.getValue().yaw());
            HubSpot spot = CapitalBuilder.hubSpot(entry.getKey());
            if (spot != null) {
                spots.put(spot, entry.getValue().pos());
                yaws.put(spot, entry.getValue().yaw());
            }
        }
        // spots the blueprint does not name fall back sensibly instead of to the classic town's coordinates
        BlockPos spawn = spots.getOrDefault(HubSpot.SPAWN, new BlockPos(0, 1, 6));
        spots.putIfAbsent(HubSpot.SPAWN, spawn);
        spots.putIfAbsent(HubSpot.RESPAWN, spawn);
        spots.putIfAbsent(HubSpot.RANKING_BOARD, spots.getOrDefault(HubSpot.HALL_OF_FAME, spawn));
        for (HubSpot spot : HubSpot.values()) {
            spots.putIfAbsent(spot, spawn);
        }
        return new HubLayout(groundY, spots, yaws, named, namedYaws);
    }
}
