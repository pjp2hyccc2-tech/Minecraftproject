package com.eternalrealm.world.forge;

import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.field.ChunkPregenPlan;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Makes the chunks of アストリア before anyone gets there (the owner, 2026-11-13: 「12チャンクごとくらいに自動読み込み
 * (通知無し)で入れて動作を軽くする」; {@link ChunkPregenPlan} decides which and in what order): a few at a time, each kept
 * loaded by a ticket until the game has generated it, then let go so it is saved and unloaded. Nothing is said to the
 * players. New chunks are only started while the server's average tick leaves room
 * ({@code performance.pregenMaxTickMillis}); how many are made at once adapts between 1 and
 * {@code performance.pregenMaxChunksInFlight}. Which chunks exist is remembered with アストリア
 * ({@code data/eternalrealm_pregen.dat}); every chunk that loads (a player walking by included) counts.
 *
 * <p>Server thread only. Off in GameTests. {@code /eternalrealm pregen status|pause|resume} for operators.</p>
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkPregen {
    private static final Logger LOG = LogUtils.getLogger();
    /** Keeps a chunk being made loaded (expires by itself should the service ever forget one). */
    private static final TicketType<ChunkPos> TICKET = TicketType.create(ModInfo.MOD_ID + "_pregen",
            Comparator.comparingLong(ChunkPos::toLong), 20 * 60);
    /** Nothing in the first minute of a server (the capital and the first chunks around the players load then). */
    private static final int START_DELAY_TICKS = 20 * 60;
    /** A chunk not made after this long is let go (and comes again later). */
    private static final int GIVE_UP_TICKS = 20 * 50;
    /** How often the players' positions are looked at for the chunks ahead of them. */
    private static final int SCAN_TICKS = 10;
    private static final int ADAPT_TICKS = 100;
    /** New chunks per tick at most: ahead of a player (urgent), and of the background pass (gentle). */
    private static final int AHEAD_STARTS_PER_TICK = 2;
    private static final int SPIRAL_STARTS_PER_TICK = 1;
    private static final int AHEAD_QUEUE = 64;
    private static final int SPIRAL_BUDGET = 4096;
    /** Below this average tick (ms) the number of chunks made at once may grow. */
    private static final double RELAXED_TICK_MILLIS = 25.0;

    private static MinecraftServer owner;
    private static long startedAt;
    private static ChunkPregenPlan plan;
    private static PregenData data;
    private static final Map<Long, Long> IN_FLIGHT = new HashMap<>();
    private static final Map<UUID, double[]> LAST_SEEN = new HashMap<>();
    private static long[] ahead = new long[0];
    private static int aheadIndex;
    private static int limit = 2;
    private static boolean paused;
    private static long made;
    private static long givenUp;

    private ChunkPregen() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server instanceof net.minecraft.gametest.framework.GameTestServer) {
            return;
        }
        ServerLevel astoria = server.getLevel(AstoriaDimension.LEVEL);
        if (astoria == null) {
            return;
        }
        if (owner != server) {
            start(server, astoria);
        }
        try {
            tick(server, astoria);
        } catch (RuntimeException e) {
            paused = true;
            LOG.error("Making the chunks of Astoria ahead failed; paused until the next start", e);
        }
    }

    /** Every chunk of アストリア that loads (generated or read) exists from now on. */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (plan == null || !(event.getLevel() instanceof ServerLevel level) || level.getServer() != owner
                || level.dimension() != AstoriaDimension.LEVEL || !(event.getChunk() instanceof LevelChunk)) {
            return;
        }
        ChunkPos pos = event.getChunk().getPos();
        if (plan.markDone(pos.x, pos.z)) {
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        owner = null;
        plan = null;
        data = null;
        IN_FLIGHT.clear(); // the tickets went with the server
        LAST_SEEN.clear();
        ahead = new long[0];
        aheadIndex = 0;
    }

    private static void start(MinecraftServer server, ServerLevel astoria) {
        owner = server;
        startedAt = server.getTickCount();
        plan = ChunkPregenPlan.astoria();
        data = PregenData.get(astoria);
        if (!plan.restore(data.saved)) {
            data.setDirty(); // another map or format: chunks that exist are found again quickly (read, not made)
        }
        data.plan = plan;
        IN_FLIGHT.clear();
        LAST_SEEN.clear();
        ahead = new long[0];
        aheadIndex = 0;
        limit = 2;
        paused = false;
        made = 0;
        givenUp = 0;
    }

    private static void tick(MinecraftServer server, ServerLevel astoria) {
        long now = server.getTickCount();
        finishOrGiveUp(astoria, now);
        if (paused || !enabled() || plan.complete() || now - startedAt < START_DELAY_TICKS) {
            return;
        }
        double tickMillis = averageTickMillis(server);
        if (now % ADAPT_TICKS == 0) {
            adapt(tickMillis);
        }
        if (tickMillis > maxTickMillis()) {
            return;
        }
        if (IN_FLIGHT.size() >= limit) {
            return;
        }
        if (now % SCAN_TICKS == 0) {
            scanAhead(server, astoria, now);
        }
        int room = Math.min(AHEAD_STARTS_PER_TICK, limit - IN_FLIGHT.size());
        while (room > 0 && aheadIndex < ahead.length) {
            if (request(astoria, ahead[aheadIndex++], now)) {
                room--;
            }
        }
        room = Math.min(SPIRAL_STARTS_PER_TICK, limit - IN_FLIGHT.size());
        if (room > 0 && aheadIndex >= ahead.length && wholeMap()) {
            for (long key : plan.nextInSpiral(room, SPIRAL_BUDGET, IN_FLIGHT::containsKey)) {
                request(astoria, key, now);
            }
        }
    }

    /** Chunks the game has made are written down and let go; chunks that take too long are let go too. */
    private static void finishOrGiveUp(ServerLevel astoria, long now) {
        Iterator<Map.Entry<Long, Long>> entries = IN_FLIGHT.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Long, Long> entry = entries.next();
            long key = entry.getKey();
            int x = ChunkPregenPlan.keyX(key);
            int z = ChunkPregenPlan.keyZ(key);
            if (astoria.getChunkSource().hasChunk(x, z)) {
                if (plan.markDone(x, z)) {
                    data.setDirty();
                }
                made++;
            } else if (now - entry.getValue() > GIVE_UP_TICKS) {
                givenUp++;
            } else {
                continue;
            }
            ChunkPos pos = new ChunkPos(x, z);
            astoria.getChunkSource().removeRegionTicket(TICKET, pos, 0, pos);
            entries.remove();
        }
    }

    /** Asks the game for a chunk (unless it is made, being made, or already loaded). @return whether it was asked */
    private static boolean request(ServerLevel astoria, long key, long now) {
        int x = ChunkPregenPlan.keyX(key);
        int z = ChunkPregenPlan.keyZ(key);
        if (IN_FLIGHT.containsKey(key) || plan.isDone(x, z)) {
            return false;
        }
        if (astoria.getChunkSource().hasChunk(x, z)) {
            if (plan.markDone(x, z)) {
                data.setDirty();
            }
            return false;
        }
        ChunkPos pos = new ChunkPos(x, z);
        astoria.getChunkSource().addRegionTicket(TICKET, pos, 0, pos);
        IN_FLIGHT.put(key, now);
        return true;
    }

    /** The chunks just beyond what the players of アストリア see, where they are heading first. */
    private static void scanAhead(MinecraftServer server, ServerLevel astoria, long now) {
        List<ChunkPregenPlan.Viewer> viewers = new ArrayList<>();
        Map<UUID, double[]> seen = new HashMap<>();
        for (ServerPlayer player : astoria.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double[] last = LAST_SEEN.get(player.getUUID());
            double vx = 0.0;
            double vz = 0.0;
            if (last != null && now > last[2] && now - last[2] <= 4L * SCAN_TICKS) {
                double ticks = now - last[2];
                vx = (player.getX() - last[0]) / ticks;
                vz = (player.getZ() - last[1]) / ticks;
            }
            seen.put(player.getUUID(), new double[]{player.getX(), player.getZ(), now});
            viewers.add(new ChunkPregenPlan.Viewer(player.getX(), player.getZ(), vx, vz));
        }
        LAST_SEEN.clear();
        LAST_SEEN.putAll(seen);
        ahead = plan.ahead(viewers, server.getPlayerList().getViewDistance(), AHEAD_QUEUE, IN_FLIGHT::containsKey);
        aheadIndex = 0;
    }

    /** More chunks at once while the server is relaxed, half as many when it is busy. */
    private static void adapt(double tickMillis) {
        if (tickMillis > maxTickMillis()) {
            limit = Math.max(1, limit / 2);
        } else if (tickMillis < RELAXED_TICK_MILLIS && IN_FLIGHT.size() >= limit) {
            limit = Math.min(limit + 1, maxInFlight());
        }
        limit = Math.min(limit, maxInFlight());
    }

    private static double averageTickMillis(MinecraftServer server) {
        long[] times = server.tickTimes;
        long sum = 0L;
        for (long time : times) {
            sum += time;
        }
        return times.length == 0 ? 0.0 : sum / (double) times.length / 1_000_000.0;
    }

    // ------------------------------------------------------------------ config

    private static boolean enabled() {
        try {
            return ERServerConfig.CHUNK_PREGEN.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static boolean wholeMap() {
        try {
            return ERServerConfig.PREGEN_WHOLE_MAP.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static int maxInFlight() {
        try {
            return ERServerConfig.PREGEN_MAX_IN_FLIGHT.get();
        } catch (IllegalStateException e) {
            return 6;
        }
    }

    private static double maxTickMillis() {
        try {
            return ERServerConfig.PREGEN_MAX_TICK_MILLIS.get();
        } catch (IllegalStateException e) {
            return 35.0;
        }
    }

    // ------------------------------------------------------------------ operators

    /** {@code /eternalrealm pregen status|pause|resume}: how far the map is made, and a switch for this session. */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eternalrealm")
                .then(Commands.literal("pregen").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(status(ctx.getSource().getServer())),
                                    false);
                            return 1;
                        }))
                        .then(Commands.literal("pause").executes(ctx -> {
                            paused = true;
                            ctx.getSource().sendSuccess(() -> Component.literal("Chunk pre-generation paused"), true);
                            return 1;
                        }))
                        .then(Commands.literal("resume").executes(ctx -> {
                            paused = false;
                            ctx.getSource().sendSuccess(() -> Component.literal("Chunk pre-generation resumed"), true);
                            return 1;
                        }))));
    }

    private static String status(MinecraftServer server) {
        if (plan == null || owner != server) {
            return "Chunk pre-generation is not running (no アストリア, or a GameTest server)";
        }
        return String.format(Locale.ROOT,
                "アストリア: %d / %d chunks made (%.1f%%), %d in flight (limit %d), %d made and %d given up this session,"
                        + " average tick %.1f ms%s%s",
                plan.doneCount(), plan.total(), 100.0 * plan.doneCount() / plan.total(), IN_FLIGHT.size(), limit, made,
                givenUp, averageTickMillis(server), enabled() ? "" : " (off in the config)", paused ? " (paused)" : "");
    }

    /** Which chunks of アストリア exist, saved with it. */
    static final class PregenData extends SavedData {
        private static final String NAME = ModInfo.MOD_ID + "_pregen";
        long[] saved = new long[0];
        /** The live plan once the service runs (what is saved). */
        ChunkPregenPlan plan;

        static PregenData get(ServerLevel astoria) {
            return astoria.getDataStorage().computeIfAbsent(PregenData::load, PregenData::new, NAME);
        }

        static PregenData load(CompoundTag tag) {
            PregenData data = new PregenData();
            data.saved = tag.getLongArray("chunks");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putLongArray("chunks", plan != null ? plan.toLongs() : saved);
            return tag;
        }
    }
}
