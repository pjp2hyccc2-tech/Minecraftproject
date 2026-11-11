package com.eternalrealm.world.forge.capital;

import com.eternalrealm.world.forge.AstoriaWorldState;
import com.eternalrealm.world.forge.FieldPieces;
import com.eternalrealm.world.forge.blueprint.BlueprintPlacer;
import com.eternalrealm.world.gen.HubSpot;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;

import java.util.function.Consumer;

/**
 * Builds the blueprint capital ({@link CapitalBlueprint}) in アストリア, a few milliseconds per server tick so the game
 * keeps running: tiles nearest the spawn first (every column of a tile is written top to bottom, which also clears
 * the previous town), then the shapes of stairs/fences/panes/walls are fitted to their neighbours, block entity data
 * (banners, signs, sunbeams...) and the decoration entities are applied, waterfalls start flowing and the natural
 * terrain is eased into the city edge. Players see the progress in the action bar; anyone a new tile buries is lifted
 * out. When done the world remembers the blueprint's hash, so this runs once per blueprint version.
 */
public final class CapitalBuilder {
    private static final Logger LOG = LogUtils.getLogger();
    /** Work per server tick. */
    private static final long BUDGET_NANOS = 14_000_000L;
    /** Natural terrain is eased into the city over this many blocks outside the footprint. */
    private static final int BLEND = 40;
    private static final String ENTITY_TAG = "eternalrealm_capital";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static Job job;
    private static Consumer<ServerLevel> onCompleted = level -> {
    };

    private CapitalBuilder() {
    }

    /** What to do once a build finished (place the NPCs at the new spots...). */
    public static void onCompleted(Consumer<ServerLevel> callback) {
        onCompleted = callback;
    }

    /** Whether a build is running. */
    public static boolean building() {
        return job != null;
    }

    /** Progress of the running build in percent (tiles placed), or -1 without a build. */
    public static int progressPercent() {
        Job current = job;
        return current == null ? -1 : current.percent();
    }

    /** Starts building {@code blueprint} unless this world already has it (or a build is running). */
    public static void ensureStarted(ServerLevel astoria, CapitalBlueprint blueprint, int groundY, BlockPos spawn) {
        if (job != null || blueprint.build().equals(stateBuild(astoria))) {
            return;
        }
        LOG.info("Building the capital blueprint {} at y={} ({} tiles)", blueprint.build(), groundY, blueprint.tiles().size());
        job = new Job(astoria, blueprint, groundY, spawn);
    }

    private static String stateBuild(ServerLevel astoria) {
        return AstoriaWorldState.get(astoria).capitalBuild();
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || job == null) {
            return;
        }
        Job current = job;
        try {
            if (current.step(System.nanoTime() + BUDGET_NANOS)) {
                job = null;
                current.finish();
                onCompleted.accept(current.level);
            }
        } catch (RuntimeException e) {
            job = null;
            LOG.error("Building the capital failed; it will be retried on the next start", e);
        }
    }

    public static void onServerStopped() {
        job = null;
    }

    // ------------------------------------------------------------------------------------------------ the job

    private static final class Job {
        private enum Phase { PLACE, BLEND, DONE }

        final ServerLevel level;
        final CapitalBlueprint blueprint;
        final int groundY;
        final BlueprintPlacer placer;
        final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;
        Phase phase = Phase.PLACE;
        int blendColumn;
        long started = System.nanoTime();
        int lastPercent = -1;

        Job(ServerLevel level, CapitalBlueprint blueprint, int groundY, BlockPos spawn) {
            this.level = level;
            this.blueprint = blueprint;
            this.groundY = groundY;
            this.placer = new BlueprintPlacer(level, blueprint, new BlockPos(0, groundY, 0), spawn.getX(), spawn.getZ(),
                    ENTITY_TAG, new BlueprintPlacer.Hooks() {
                        @Override
                        public void afterTile(int x0, int z0, int size) {
                            liftBuriedPlayers(x0, z0, size);
                        }

                        @Override
                        public void progress(int percent) {
                            Job.this.progress(percent);
                        }
                    }, problems -> LOG.error("{} capital palette entries are invalid and left out: {}", problems.size(),
                    problems));
            this.minX = placer.minX();
            this.maxX = placer.maxX();
            this.minZ = placer.minZ();
            this.maxZ = placer.maxZ();
        }

        int percent() {
            return placer.percent();
        }

        /** @return true when everything is done */
        boolean step(long deadline) {
            while (System.nanoTime() < deadline) {
                switch (phase) {
                    case PLACE -> {
                        if (!placer.step(deadline)) {
                            return false;
                        }
                        phase = Phase.BLEND;
                        blendColumn = 0;
                    }
                    case BLEND -> {
                        if (!blend(deadline)) {
                            return false;
                        }
                        phase = Phase.DONE;
                    }
                    case DONE -> {
                        return true;
                    }
                }
            }
            return phase == Phase.DONE;
        }

        private void liftBuriedPlayers(int x0, int z0, int size) {
            for (ServerPlayer player : level.players()) {
                BlockPos feet = player.blockPosition();
                if (feet.getX() < x0 || feet.getX() >= x0 + size || feet.getZ() < z0 || feet.getZ() >= z0 + size) {
                    continue;
                }
                if (!level.getBlockState(feet).isSuffocating(level, feet)
                        && !level.getBlockState(feet.above()).isSuffocating(level, feet.above())) {
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ());
                player.teleportTo(level, player.getX(), top, player.getZ(), player.getYRot(), player.getXRot());
            }
        }

        private void progress(int percent) {
            if (percent == lastPercent) {
                return;
            }
            lastPercent = percent;
            Component message = Component.translatable("message.eternalrealm.capital.building", percent)
                    .withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(message, true);
            }
        }

        // ---- easing the natural terrain into the city edge

        /** The ground of column (x, z) under {@code top}: the first block down that is soil or rock (not a tree). */
        private int ground(int x, int z, int top) {
            for (int y = top; y > top - 40 && y > level.getMinBuildHeight(); y--) {
                cursor.set(x, y, z);
                BlockState state = level.getBlockState(cursor);
                if (state.isAir() || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS)
                        || state.canBeReplaced() || !state.getFluidState().isEmpty() && !state.isSolid()) {
                    if (!state.getFluidState().isEmpty()) {
                        return y; // water: the caller leaves it alone
                    }
                    continue;
                }
                return y;
            }
            return top;
        }

        private boolean blend(long deadline) {
            int width = maxX - minX + 1 + 2 * BLEND;
            int depth = maxZ - minZ + 1 + 2 * BLEND;
            int total = width * depth;
            BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
            BlockState dirt = Blocks.DIRT.defaultBlockState();
            while (blendColumn < total) {
                if ((blendColumn & 63) == 0 && System.nanoTime() >= deadline) {
                    return false;
                }
                int i = blendColumn++;
                int x = minX - BLEND + i / depth;
                int z = minZ - BLEND + i % depth;
                int dx = Math.max(minX - x, x - maxX);
                int dz = Math.max(minZ - z, z - maxZ);
                int distance = Math.max(dx, dz);
                if (distance <= 0 || distance > BLEND || FieldPieces.covers(x, z)) {
                    continue;   // the field set pieces outside the gates (waystone shrine, road lanterns) stay
                }
                level.getChunk(x >> 4, z >> 4);
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int natural = ground(x, z, top);
                cursor.set(x, natural, z);
                if (!level.getFluidState(cursor).isEmpty() || !level.getFluidState(cursor.above()).isEmpty()) {
                    continue; // leave lakes and rivers alone
                }
                float t = distance / (float) BLEND;
                t = t * t * (3.0F - 2.0F * t);
                int target = Math.round(Mth.lerp(t, groundY, natural));
                if (target == natural) {
                    continue;
                }
                if (target < natural) {
                    // cut the hill down, and whatever stood on it (trees, plants), so nothing is left floating
                    for (int y = Math.max(natural, top); y > target; y--) {
                        cursor.set(x, y, z);
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                } else {
                    for (int y = natural + 1; y < target; y++) {
                        cursor.set(x, y, z);
                        level.setBlock(cursor, dirt, FLAGS);
                    }
                }
                cursor.set(x, target, z);
                level.setBlock(cursor, grass, FLAGS);
            }
            return true;
        }

        void finish() {
            AstoriaWorldState.get(level).markCapitalBuilt(blueprint.build(), groundY);
            long ms = (System.nanoTime() - started) / 1_000_000L;
            LOG.info("Built the capital blueprint {}: {} blocks changed, {} shaped, {} block entities, {} entities in {} s",
                    blueprint.build(), placer.placed(), placer.shapedCount(), blueprint.blockEntities().size(),
                    blueprint.entities().size(), ms / 1000);
            Component message = Component.translatable("message.eternalrealm.capital.built").withStyle(ChatFormatting.GOLD);
            for (ServerPlayer player : level.players()) {
                player.displayClientMessage(message, true);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ layout

    /** Spot names of the blueprint for the classic hub spots. */
    public static HubSpot hubSpot(String name) {
        return switch (name) {
            case "spawn" -> HubSpot.SPAWN;
            case "respawn" -> HubSpot.RESPAWN;
            case "npc.quest_board" -> HubSpot.QUEST_BOARD;
            case "npc.item_shop" -> HubSpot.ITEM_SHOP;
            case "npc.blacksmith" -> HubSpot.BLACKSMITH;
            case "npc.job_master" -> HubSpot.JOB_MASTER;
            case "npc.auction" -> HubSpot.AUCTION_HOUSE;
            case "npc.mailbox" -> HubSpot.MAILBOX;
            case "npc.guild_registrar" -> HubSpot.GUILD_REGISTRAR;
            case "npc.gatekeeper" -> HubSpot.GATEKEEPER;
            case "npc.arena_master" -> HubSpot.ARENA_MASTER;
            case "npc.hall_of_fame" -> HubSpot.HALL_OF_FAME;
            case "board.ranking" -> HubSpot.RANKING_BOARD;
            default -> null;
        };
    }
}
