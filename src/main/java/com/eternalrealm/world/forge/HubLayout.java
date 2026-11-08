package com.eternalrealm.world.forge;

import com.eternalrealm.world.gen.HubSpot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Block positions of the capital アルセリア王都 (plaza centre at x = 0, z = 0). Every {@link HubSpot} is a "feet"
 * position: the NPC or player stands in that block, on the paving one block below. Immutable.
 *
 * <p>The floor height is chosen from the noise terrain (median around the plaza, at least one block above the sea),
 * so the layout is known before the city is built and never changes afterwards.</p>
 */
public final class HubLayout {
    private final int groundY;

    HubLayout(int groundY) {
        this.groundY = groundY;
    }

    /** The layout of the capital in this アストリア level (built or not). */
    public static HubLayout of(ServerLevel astoria) {
        return new HubLayout(groundY(astoria));
    }

    /** y of the plaza paving. */
    public int groundY() {
        return groundY;
    }

    /** Feet position of a named spot. */
    public BlockPos position(HubSpot spot) {
        return new BlockPos(spot.dx(), groundY + spot.dy(), spot.dz());
    }

    /** Facing (Minecraft yaw) for an NPC or a player placed at the spot. */
    public float yaw(HubSpot spot) {
        return spot.yaw();
    }

    public Map<HubSpot, BlockPos> positions() {
        Map<HubSpot, BlockPos> positions = new EnumMap<>(HubSpot.class);
        for (HubSpot spot : HubSpot.values()) {
            positions.put(spot, position(spot));
        }
        return positions;
    }

    public BlockPos spawnPoint() {
        return position(HubSpot.SPAWN);
    }

    public BlockPos respawnPoint() {
        return position(HubSpot.RESPAWN);
    }

    public BlockPos questBoard() {
        return position(HubSpot.QUEST_BOARD);
    }

    public BlockPos itemShop() {
        return position(HubSpot.ITEM_SHOP);
    }

    public BlockPos blacksmith() {
        return position(HubSpot.BLACKSMITH);
    }

    public BlockPos jobMaster() {
        return position(HubSpot.JOB_MASTER);
    }

    public BlockPos auctionHouse() {
        return position(HubSpot.AUCTION_HOUSE);
    }

    public BlockPos mailbox() {
        return position(HubSpot.MAILBOX);
    }

    public BlockPos guildRegistrar() {
        return position(HubSpot.GUILD_REGISTRAR);
    }

    public BlockPos gatekeeper() {
        return position(HubSpot.GATEKEEPER);
    }

    public BlockPos arenaMaster() {
        return position(HubSpot.ARENA_MASTER);
    }

    public BlockPos rankingBoard() {
        return position(HubSpot.RANKING_BOARD);
    }

    public BlockPos hallOfFame() {
        return position(HubSpot.HALL_OF_FAME);
    }

    /** The built floor height if the capital exists, otherwise the planned one (cached in the saved state). */
    static int groundY(ServerLevel astoria) {
        AstoriaWorldState state = AstoriaWorldState.get(astoria);
        if (state.hubGroundY().isPresent()) {
            return state.hubGroundY().getAsInt();
        }
        return state.plannedHubGroundY().orElseGet(() -> {
            int planned = plannedGroundY(astoria);
            state.planHub(planned);
            return planned;
        });
    }

    /** Median noise height over the walled area, at least one block above the sea and at most y = 110. */
    private static int plannedGroundY(ServerLevel astoria) {
        ChunkGenerator generator = astoria.getChunkSource().getGenerator();
        RandomState random = astoria.getChunkSource().randomState();
        List<Integer> heights = new ArrayList<>();
        for (int x = -40; x <= 40; x += 10) {
            for (int z = -40; z <= 40; z += 10) {
                heights.add(generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, astoria, random) - 1);
            }
        }
        Collections.sort(heights);
        return Mth.clamp(heights.get(heights.size() / 2), generator.getSeaLevel() + 1, 110);
    }
}
