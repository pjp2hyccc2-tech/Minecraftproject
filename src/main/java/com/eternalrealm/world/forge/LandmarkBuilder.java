package com.eternalrealm.world.forge;

import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.world.gen.LandmarkSite;
import com.eternalrealm.world.gen.LandmarkSites;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the dungeon entrances of アストリア lazily: once a second it checks the players in the dimension and builds
 * every landmark that one of them has come within {@link #TRIGGER_RADIUS} blocks of (and the capital within
 * {@link #HUB_TRIGGER_RADIUS}) as soon as the chunks it covers are loaded, so building does not generate terrain on
 * the server thread. A player closer than {@link #FORCE_RADIUS} forces the build (missing chunks are generated then).
 * Each landmark is built once per world (see {@link AstoriaWorldState}).
 */
public final class LandmarkBuilder {
    private static final Logger LOG = LogUtils.getLogger();
    public static final int TRIGGER_RADIUS = 80;
    public static final int HUB_TRIGGER_RADIUS = 160;
    public static final int FORCE_RADIUS = 40;
    private static final long RETRY_TICKS = 20L * 60 * 5;
    /** dungeon id -> game time before which a failed build is not retried. */
    private static final Map<String, Long> RETRY_AFTER = new ConcurrentHashMap<>();

    private LandmarkBuilder() {
    }

    /** Forge bus listener (registered in EternalRealm). */
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 20 != 7) {
            return;
        }
        ServerLevel astoria = server.getLevel(AstoriaDimension.LEVEL);
        if (astoria == null || astoria.players().isEmpty()) {
            return;
        }
        try {
            tick(astoria);
        } catch (RuntimeException e) {
            LOG.error("Building the landmarks of Astoria failed", e);
        }
    }

    private static void tick(ServerLevel astoria) {
        AstoriaWorldState state = AstoriaWorldState.get(astoria);
        if (!state.hubBuilt() && anyPlayerWithin(astoria, 0, 0, HUB_TRIGGER_RADIUS)
                && (TerrainShaper.chunksLoaded(astoria, 0, 0, HubBuilder.BLEND + 12)
                || anyPlayerWithin(astoria, 0, 0, HubBuilder.WALL + FORCE_RADIUS))) {
            HubBuilder.ensureBuilt(astoria);
        }
        long now = astoria.getGameTime();
        for (LandmarkSite site : LandmarkSites.all()) {
            if (state.landmarkBuilt(site.dungeonId()) || now < RETRY_AFTER.getOrDefault(site.dungeonId(), Long.MIN_VALUE)
                    || !anyPlayerWithin(astoria, site.x(), site.z(), TRIGGER_RADIUS)) {
                continue;
            }
            if (!TerrainShaper.chunksLoaded(astoria, site.x(), site.z(), site.padRadius() + 16)
                    && !anyPlayerWithin(astoria, site.x(), site.z(), FORCE_RADIUS)) {
                continue; // wait until the player's view has loaded the area
            }
            try {
                ensureBuilt(astoria, site.dungeonId());
            } catch (RuntimeException e) {
                RETRY_AFTER.put(site.dungeonId(), now + RETRY_TICKS);
                LOG.error("Building the entrance of dungeon {} failed; retrying in 5 minutes", site.dungeonId(), e);
            }
        }
    }

    private static boolean anyPlayerWithin(ServerLevel level, int x, int z, int radius) {
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz <= (double) radius * radius) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the entrance of a dungeon now if it does not exist yet.
     *
     * @return true if it was built by this call, false if it already existed
     * @throws IllegalArgumentException for an unknown dungeon id
     */
    public static boolean ensureBuilt(ServerLevel astoria, String dungeonId) {
        LandmarkSite site = LandmarkSites.byId(dungeonId)
                .orElseThrow(() -> new IllegalArgumentException("unknown dungeon " + dungeonId));
        AstoriaWorldState state = AstoriaWorldState.get(astoria);
        if (state.landmarkBuilt(dungeonId)) {
            return false;
        }
        int groundY = groundY(astoria, site);
        long start = System.nanoTime();
        build(astoria, site, groundY);
        state.markLandmarkBuilt(dungeonId, groundY);
        LOG.info("Built the entrance of {} at {} {} {} in {} ms", dungeonId, site.x(), groundY, site.z(),
                (System.nanoTime() - start) / 1_000_000L);
        return true;
    }

    public static boolean isBuilt(ServerLevel astoria, String dungeonId) {
        return AstoriaWorldState.get(astoria).landmarkBuilt(dungeonId);
    }

    /** Floor height of a landmark: built value, or derived from the noise terrain exactly as the build will. */
    static int groundY(ServerLevel astoria, LandmarkSite site) {
        AstoriaWorldState state = AstoriaWorldState.get(astoria);
        if (state.landmarkGroundY(site.dungeonId()).isPresent()) {
            return state.landmarkGroundY(site.dungeonId()).getAsInt();
        }
        ChunkGenerator generator = astoria.getChunkSource().getGenerator();
        RandomState random = astoria.getChunkSource().randomState();
        if (site.theme() == DungeonTheme.DARK_TOWER) {
            return DarkTowerShape.groundY(generator, astoria, random);
        }
        int half = site.padRadius() / 2;
        List<Integer> heights = new ArrayList<>();
        for (int dx = -half; dx <= half; dx += half) {
            for (int dz = -half; dz <= half; dz += half) {
                heights.add(generator.getBaseHeight(site.x() + dx, site.z() + dz, Heightmap.Types.OCEAN_FLOOR_WG, astoria,
                        random) - 1);
            }
        }
        Collections.sort(heights);
        return Mth.clamp(heights.get(heights.size() / 2), generator.getSeaLevel() + 1, 200);
    }

    private static void build(ServerLevel level, LandmarkSite site, int groundY) {
        LandmarkStructures.Palette palette = LandmarkStructures.Palette.of(site.theme());
        Rotation rotation = Rotation.values()[Math.floorMod(site.quarterTurns(), 4)];
        Painter painter = new Painter(level, new BlockPos(site.x(), groundY, site.z()), rotation);
        if (site.theme() == DungeonTheme.DARK_TOWER) {
            TerrainShaper.loadChunks(level, site.x(), site.z(), site.padRadius() + 16);
            TerrainShaper.level(level, site.x(), site.z(), DarkTowerShape.PLINTH_RADIUS, site.padRadius(), false, groundY,
                    palette.top().defaultBlockState(), palette.filler().defaultBlockState(),
                    (dx, dz) -> DarkTowerShape.octagon(dx, dz) <= DarkTowerShape.PLINTH_RADIUS + 0.5);
            LandmarkStructures.towerGate(painter, site);
        } else {
            TerrainShaper.loadChunks(level, site.x(), site.z(), site.padRadius() + 16);
            TerrainShaper.level(level, site.x(), site.z(), site.padRadius() - 4, site.padRadius() + 6, false, groundY,
                    palette.top().defaultBlockState(), palette.filler().defaultBlockState());
            LandmarkStructures.gate(painter, site.theme(), site);
        }
        painter.finish();
    }
}
