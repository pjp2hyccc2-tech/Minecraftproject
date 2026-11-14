package com.eternalrealm.world.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.field.Shores;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiPredicate;

/**
 * Mends the shores of アストリア so whoever falls into the water can climb out (owner, 2026-11-14: 「川に落ちたら登れなく
 * なってるから川沿いのブロックを1段ずつ下げて」; the rules are {@link Shores}). Every chunk near water is mended once, when
 * it is loaded with its four neighbours (two chunks a tick at most); the chunks done are saved with アストリア, so a
 * bank a player builds up again later stays as built. Server thread (the load event only queues).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShorePatch {
    private static final Logger LOG = LogUtils.getLogger();
    /** Chunks mended per server tick at most. */
    private static final int PER_TICK = 2;
    /** Block updates: tell the clients, do not wake the neighbours (no water starts to flow, nothing falls). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final ConcurrentLinkedQueue<Long> LOADED = new ConcurrentLinkedQueue<>();
    private static final Set<Long> QUEUED = new HashSet<>();
    private static final List<Long> QUEUE = new ArrayList<>();
    private static long mended;

    private ShorePatch() {
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Realm.ASTORIA
                && event.getChunk() instanceof LevelChunk chunk) {
            LOADED.add(chunk.getPos().toLong());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerLevel level = event.getServer().getLevel(Realm.ASTORIA);
        if (level == null) {
            LOADED.clear();
            return;
        }
        Done done = Done.get(level);
        for (Long key = LOADED.poll(); key != null; key = LOADED.poll()) {
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            consider(done, cx, cz);
            // a chunk that waited for this one as its neighbour may be ready now
            consider(done, cx + 1, cz);
            consider(done, cx - 1, cz);
            consider(done, cx, cz + 1);
            consider(done, cx, cz - 1);
        }
        int budget = PER_TICK;
        for (int i = 0; i < QUEUE.size() && budget > 0; ) {
            long key = QUEUE.get(i);
            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                drop(i, key); // unloaded: it comes back when it loads again
                continue;
            }
            if (!loaded(level, cx + 1, cz) || !loaded(level, cx - 1, cz) || !loaded(level, cx, cz + 1)
                    || !loaded(level, cx, cz - 1)) {
                drop(i, key); // a neighbour's load brings it back
                continue;
            }
            drop(i, key);
            int minX = chunk.getPos().getMinBlockX();
            int minZ = chunk.getPos().getMinBlockZ();
            int lowered = mend(level, minX, minZ, minX + 15, minZ + 15,
                    (x, z) -> Shores.mayChange(x, z) && !FieldPieces.near(x, z, 2));
            done.add(key);
            mended += lowered;
            budget--;
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED.clear();
        QUEUED.clear();
        QUEUE.clear();
        if (mended > 0) {
            LOG.info("Mended {} shore columns of アストリア so swimmers can climb out", mended);
        }
        mended = 0;
    }

    private static void consider(Done done, int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (!QUEUED.contains(key) && !done.has(key) && Shores.chunkNearWater(cx, cz)) {
            QUEUED.add(key);
            QUEUE.add(key);
        }
    }

    private static void drop(int index, long key) {
        QUEUE.remove(index);
        QUEUED.remove(key);
    }

    private static boolean loaded(ServerLevel level, int cx, int cz) {
        return level.getChunkSource().getChunkNow(cx, cz) != null;
    }

    // ------------------------------------------------------------------ mending

    /** What stands in a column: its ground's top block, the water's top block above it (if any). */
    private record Column(int ground, int water, BlockState top) {
        static final int NO_WATER = Integer.MIN_VALUE;

        boolean wet() {
            return water != NO_WATER;
        }
    }

    /**
     * Mends the shores in the block box {@code [minX..maxX] × [minZ..maxZ]} (its neighbours are looked at, not
     * changed, except by the ripple of a lowered column): the columns {@code may} allows. @return columns lowered
     */
    public static int mend(ServerLevel level, int minX, int minZ, int maxX, int maxZ,
                           BiPredicate<Integer, Integer> may) {
        Map<Long, Column> columns = new HashMap<>();
        Map<Long, Integer> loweredTo = new HashMap<>();
        Map<Long, Integer> waterOf = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Column column = column(level, columns, x, z);
                if (column.wet() || !may.test(x, z)) {
                    continue;
                }
                int water = Column.NO_WATER;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    Column next = column(level, columns, x + direction.getStepX(), z + direction.getStepZ());
                    if (next.wet() && Shores.lowers(column.ground(), next.water())) {
                        water = Math.max(water, next.water());
                    }
                }
                if (water != Column.NO_WATER && lower(level, x, column.ground(), z)) {
                    long key = BlockPos.asLong(x, 0, z);
                    loweredTo.put(key, column.ground() - 1);
                    waterOf.put(key, water);
                    columns.remove(key);
                }
            }
        }
        // the ripple: a neighbour farther out that would now be a wall of two goes down too
        int count = loweredTo.size();
        for (Map.Entry<Long, Integer> entry : new ArrayList<>(loweredTo.entrySet())) {
            int x = BlockPos.getX(entry.getKey());
            int z = BlockPos.getZ(entry.getKey());
            int water = waterOf.get(entry.getKey());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                int nx = x + direction.getStepX();
                int nz = z + direction.getStepZ();
                long key = BlockPos.asLong(nx, 0, nz);
                if (loweredTo.containsKey(key) || !may.test(nx, nz)) {
                    continue;
                }
                Column next = column(level, columns, nx, nz);
                if (!next.wet() && Shores.ripples(next.ground(), entry.getValue(), water)
                        && lower(level, nx, next.ground(), nz)) {
                    loweredTo.put(key, next.ground() - 1);
                    columns.remove(key);
                    count++;
                }
            }
        }
        return count;
    }

    private static Column column(ServerLevel level, Map<Long, Column> cache, int x, int z) {
        return cache.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> probe(level, x, z));
    }

    /** What the patch sees in a column (GameTests' failure messages). */
    public static String describeForTest(ServerLevel level, int x, int z) {
        Column column = probe(level, x, z);
        return "(" + x + ", " + z + ": surface " + level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + ", ground "
                + column.ground() + " " + column.top() + (column.wet() ? ", water " + column.water() : ", dry") + ")";
    }

    /** From the surface down through plants: standing water on top, or the ground's top block. */
    private static Column probe(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int bottom = Math.max(level.getMinBuildHeight(), y - 48);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        int water = Column.NO_WATER;
        for (; y >= bottom; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            FluidState fluid = state.getFluidState();
            if (fluid.is(FluidTags.WATER)) {
                if (water == Column.NO_WATER) {
                    if (!fluid.isSource()) {
                        return new Column(y, Column.NO_WATER, state); // flowing water: no shore to mend here
                    }
                    water = y;
                }
                continue;
            }
            if (state.isAir() || plant(state) || state.is(BlockTags.LEAVES) && water == Column.NO_WATER) {
                continue; // through plants, and through a canopy hanging over the water or the bank
            }
            return new Column(y, water, state);
        }
        return new Column(bottom, Column.NO_WATER, Blocks.AIR.defaultBlockState());
    }

    /** Small plants that go with the ground they grow on (grass, flowers, ferns, saplings, snow layers). */
    private static boolean plant(BlockState state) {
        return state.getBlock() instanceof BushBlock || state.is(BlockTags.REPLACEABLE) || state.is(Blocks.SNOW);
    }

    /**
     * Lowers the column whose ground's top block is at {@code top} by one block: that block moves down one (its
     * grass, sand or stone stays the surface), a plant on it goes. Only natural ground under nothing but air or a
     * plant. @return whether it was lowered
     */
    static boolean lower(ServerLevel level, int x, int top, int z) {
        BlockPos topPos = new BlockPos(x, top, z);
        BlockState surface = level.getBlockState(topPos);
        String id = String.valueOf(ForgeRegistries.BLOCKS.getKey(surface.getBlock()));
        if (!Shores.natural(id)) {
            return false;
        }
        BlockPos above = topPos.above();
        BlockState over = level.getBlockState(above);
        if (!over.isAir()) {
            if (!plant(over)) {
                return false; // a log, a fence, something built: leave the column as it is
            }
            if (over.getBlock() instanceof DoublePlantBlock && level.getBlockState(above.above()).is(over.getBlock())) {
                level.setBlock(above.above(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
            level.setBlock(above, Blocks.AIR.defaultBlockState(), FLAGS);
        }
        level.setBlock(topPos.below(), surface, FLAGS);
        level.setBlock(topPos, Blocks.AIR.defaultBlockState(), FLAGS);
        return true;
    }

    /** The chunks of アストリア whose shores were mended, saved with it. */
    static final class Done extends SavedData {
        private static final String NAME = ModInfo.MOD_ID + "_shores";
        /** Raise to mend every shore again under new rules. */
        private static final int RULES = 1;
        private final Set<Long> chunks = new HashSet<>();

        static Done get(ServerLevel astoria) {
            return astoria.getDataStorage().computeIfAbsent(Done::load, Done::new, NAME);
        }

        static Done load(CompoundTag tag) {
            Done done = new Done();
            if (tag.getInt("rules") == RULES) {
                for (long key : tag.getLongArray("chunks")) {
                    done.chunks.add(key);
                }
            }
            return done;
        }

        boolean has(long key) {
            return chunks.contains(key);
        }

        void add(long key) {
            if (chunks.add(key)) {
                setDirty();
            }
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            long[] keys = new long[chunks.size()];
            int i = 0;
            for (long key : chunks) {
                keys[i++] = key;
            }
            tag.putInt("rules", RULES);
            tag.putLongArray("chunks", keys);
            return tag;
        }
    }
}
