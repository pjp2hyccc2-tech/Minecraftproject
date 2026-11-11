package com.eternalrealm.estate.forge;

import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.estate.EstateData;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstatePlot;
import com.eternalrealm.estate.EstateRecord;
import com.eternalrealm.estate.EstateRegistry;
import com.eternalrealm.estate.EstateRepository;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.eternalrealm.world.forge.blueprint.BlueprintPlacer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The estates of one running server (behind the static {@link EstateService}): the registry (loaded once, saved
 * through the I/O executor whenever it changes), and the work on the plots — builds, moves after a redesign and
 * wipes — done a few milliseconds per tick, one job at a time. Server thread only.
 *
 * <p>Started, it compares the registry with what the world says stands ({@link EstateWorldState}): builds nobody owns
 * are wiped (what is inside goes to the last owner's keepsafe), missing builds are built, and an interrupted move is
 * finished.</p>
 */
final class EstateManager {
    private static final Logger LOG = LogUtils.getLogger();
    private static final UUID IO_KEY = new UUID(0L, 0x657374617465L);
    /** Building and wiping time per server tick. */
    private static final long BUDGET_NANOS = 6_000_000L;
    /** Persistent-data flag of the entities a build placed. */
    static final String ENTITY_TAG = "eternalrealm_estate";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    final MinecraftServer server;
    final ServerLevel level;
    private final EstateRepository repository;
    private final KeyedSerialExecutor<UUID> io;
    final EstateRegistry registry;
    final boolean available;
    private final Consumer<EstateKey> onReady;
    private long savedRevision;
    private boolean saveInFlight;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private Job current;

    EstateManager(MinecraftServer server, ServerLevel level, EstateRepository repository, KeyedSerialExecutor<UUID> io,
                  Consumer<EstateKey> onReady) {
        this.server = server;
        this.level = level;
        this.repository = repository;
        this.io = io;
        this.onReady = onReady;
        EstateRegistry loaded;
        boolean ok;
        try {
            loaded = repository.load().map(EstateRegistry::of).orElseGet(EstateRegistry::new);
            ok = true;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Could not load the estates; rooms and guild houses stay closed until the data is fixed", e);
            loaded = new EstateRegistry();
            ok = false;
        }
        this.registry = loaded;
        this.available = ok;
        this.savedRevision = loaded.revision();
        if (ok) {
            reconcile();
        }
        LOG.info("Estates: {} estate(s), {} plot(s) waiting to be wiped, {} job(s) queued", registry.all().size(),
                registry.wiping().size(), jobs.size());
    }

    EstateWorldState state() {
        return EstateWorldState.get(level);
    }

    // ------------------------------------------------------------------ start-up

    private void reconcile() {
        EstateWorldState world = state();
        for (Map.Entry<EstatePlot, EstateWorldState.Built> entry : world.builtPlots().entrySet()) {
            if (registry.atPlot(entry.getKey()).isEmpty() && registry.markOrphan(entry.getKey())) {
                LOG.warn("Estates: {} ({}) belongs to no estate; it will be wiped", entry.getKey(),
                        entry.getValue().key());
            }
        }
        for (EstatePlot plot : registry.wiping()) {
            queueWipe(plot, world.built(plot).map(EstateWorldState.Built::owner).orElse(null));
        }
        for (EstateRecord record : registry.all()) {
            if (record.moving() && builtAs(record.plot(), record.key().design()).isPresent()) {
                jobs.add(new MoveJob(record.id()));
            } else if (builtAs(record.plot(), record.key().design()).isEmpty()) {
                queueBuild(record);
            }
        }
    }

    // ------------------------------------------------------------------ queries

    /** The finished build on {@code plot} if it is of {@code design}. */
    Optional<EstateWorldState.Built> builtAs(EstatePlot plot, String design) {
        return state().built(plot).filter(built -> built.design().equals(design));
    }

    /** The build players use for estate {@code record}, if it stands. */
    Optional<EstateWorldState.Built> liveBuild(EstateRecord record) {
        return record.moving() ? state().built(record.previous()) : builtAs(record.plot(), record.key().design());
    }

    EstateStatus status(EstateRecord record) {
        if (record.moving()) {
            return new EstateStatus(liveBuild(record).isPresent() ? EstateStatus.Phase.REBUILDING
                    : EstateStatus.Phase.BUILDING, percent(record.plot()));
        }
        return liveBuild(record).isPresent() ? new EstateStatus(EstateStatus.Phase.READY, 100)
                : new EstateStatus(EstateStatus.Phase.BUILDING, percent(record.plot()));
    }

    /** Progress of the build on {@code plot} (0 while it waits its turn). */
    int percent(EstatePlot plot) {
        if (current instanceof BuildJob build && build.plot.equals(plot)) {
            return build.percent();
        }
        return 0;
    }

    /** Jobs queued or running. */
    int pendingJobs() {
        return jobs.size() + (current == null ? 0 : 1);
    }

    // ------------------------------------------------------------------ changes

    EstateResult claim(EstateKey key) {
        if (!available) {
            return EstateResult.UNAVAILABLE;
        }
        if (EstateBlueprints.get(server, key).isEmpty()) {
            return EstateResult.UNKNOWN_DESIGN;
        }
        EstateRegistry.Change change = registry.claim(key, System.currentTimeMillis());
        change.record().ifPresent(this::queueBuild);
        saveIfDirty();
        return change.result();
    }

    /** Rebuilds an estate as {@code design} (the same design again if its blueprint is newer: a refurbishment). */
    EstateResult move(EstateRecord record, String design) {
        if (!available) {
            return EstateResult.UNAVAILABLE;
        }
        if (record.moving()) {
            return EstateResult.BUSY;
        }
        Optional<EstateWorldState.Built> live = liveBuild(record);
        if (live.isEmpty()) {
            return EstateResult.BUILDING;
        }
        Optional<Blueprint> blueprint = EstateBlueprints.get(server, record.key().kind(), design);
        if (blueprint.isEmpty()) {
            return EstateResult.UNKNOWN_DESIGN;
        }
        if (design.equals(record.key().design()) && blueprint.get().build().equals(live.get().build())) {
            return EstateResult.SAME_DESIGN;
        }
        EstateRegistry.Change change = registry.startMove(record.id(), design);
        change.record().ifPresent(this::queueBuild);
        saveIfDirty();
        return change.result();
    }

    /** Starts a refurbishment if the estate's build is older than its design's blueprint. */
    void refreshIfOutdated(EstateRecord record) {
        if (record.moving()) {
            return;
        }
        Optional<EstateWorldState.Built> live = liveBuild(record);
        Optional<Blueprint> blueprint = EstateBlueprints.get(server, record.key());
        if (live.isPresent() && blueprint.isPresent() && !blueprint.get().build().equals(live.get().build())) {
            LOG.info("Estates: refurbishing {} to the newer blueprint {}", record.id(), blueprint.get().build());
            move(record, record.key().design());
        }
    }

    Optional<EstateRecord> release(EstateRecord record) {
        Optional<EstateRecord> released = registry.release(record.id());
        released.ifPresent(gone -> {
            sendOutOf(gone.plot());
            gone.previousPlot().ifPresent(this::sendOutOf);
            // a build that never finished is not in the world's records: its blueprint tells where it reaches
            AABB hint = EstateLevels.box(gone.plot(), EstateBlueprints.get(server, gone.key()).orElse(null), level);
            queueWipe(gone.plot(), gone.key().owner(), hint);
            gone.previousPlot().ifPresent(previous -> queueWipe(previous, gone.key().owner(), null));
            saveIfDirty();
        });
        return released;
    }

    /** Players inside {@code plot} go back to where they came from. */
    void sendOutOf(EstatePlot plot) {
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (plot.contains(player.getBlockX(), player.getBlockZ())) {
                EstateService.leave(player);
            }
        }
    }

    // ------------------------------------------------------------------ the clock

    void tick() {
        long deadline = System.nanoTime() + BUDGET_NANOS;
        while (System.nanoTime() < deadline) {
            if (current == null) {
                current = jobs.poll();
                if (current == null) {
                    break;
                }
            }
            boolean done;
            try {
                done = current.step(deadline);
            } catch (RuntimeException e) {
                LOG.error("Estates: work on {} failed; it is retried on the next start", current.plot, e);
                done = true;
            }
            if (!done) {
                break;
            }
            current = null;
        }
        if (server.getTickCount() % 20 == 7) {
            saveIfDirty();
        }
    }

    // ------------------------------------------------------------------ saving

    void saveIfDirty() {
        if (!available || saveInFlight || registry.revision() == savedRevision) {
            return;
        }
        EstateData snapshot = registry.snapshot();
        long revision = registry.revision();
        if (io == null || io.isClosed()) {
            saveNow(snapshot, revision);
            return;
        }
        saveInFlight = true;
        io.submit(IO_KEY, () -> {
            boolean ok = write(snapshot);
            server.execute(() -> {
                saveInFlight = false;
                if (ok && revision > savedRevision) {
                    savedRevision = revision;
                }
            });
            return null;
        });
    }

    /** Server stopping: the last state is queued behind any save still running (the executor finishes both). */
    void flush() {
        if (!available || registry.revision() == savedRevision) {
            return;
        }
        EstateData snapshot = registry.snapshot();
        long revision = registry.revision();
        if (io == null || io.isClosed()) {
            saveNow(snapshot, revision);
        } else {
            io.submit(IO_KEY, () -> write(snapshot));
            savedRevision = revision;
        }
    }

    private void saveNow(EstateData snapshot, long revision) {
        if (write(snapshot)) {
            savedRevision = revision;
        }
    }

    private boolean write(EstateData snapshot) {
        try {
            repository.save(snapshot);
            return true;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Saving the estates failed; will retry", e);
            return false;
        }
    }

    // ------------------------------------------------------------------ jobs

    private void queueBuild(EstateRecord record) {
        Optional<Blueprint> blueprint = EstateBlueprints.get(server, record.key());
        if (blueprint.isEmpty()) {
            LOG.error("Estates: no blueprint {} for {}; it cannot be built", record.key().blueprintFolder(), record.id());
            return;
        }
        cancel(record.plot());
        jobs.add(new BuildJob(record.id(), record.key(), record.plot(), blueprint.get()));
    }

    private void queueWipe(EstatePlot plot, UUID owner) {
        queueWipe(plot, owner, null);
    }

    private void queueWipe(EstatePlot plot, UUID owner, AABB hint) {
        cancel(plot);
        jobs.add(new WipeJob(plot, owner, hint));
    }

    /** Drops queued work on {@code plot} (a running build of it stops). */
    private void cancel(EstatePlot plot) {
        jobs.removeIf(job -> job.plot.equals(plot));
        if (current != null && current.plot.equals(plot)) {
            current = null;
        }
    }

    private abstract static class Job {
        final EstatePlot plot;

        Job(EstatePlot plot) {
            this.plot = plot;
        }

        /** @return true when the job is over */
        abstract boolean step(long deadline);
    }

    /** Builds a design on a fresh plot, then finishes a move or reports the estate ready. */
    private final class BuildJob extends Job {
        private enum Stage { LOAD, CLEAN, PLACE }

        final String id;
        final EstateKey key;
        final Blueprint blueprint;
        final AABB box;
        BlueprintPlacer placer;
        Stage stage = Stage.LOAD;
        int waited;

        BuildJob(String id, EstateKey key, EstatePlot plot, Blueprint blueprint) {
            super(plot);
            this.id = id;
            this.key = key;
            this.blueprint = blueprint;
            this.box = EstateLevels.box(plot, blueprint, level);
        }

        int percent() {
            return placer == null ? 0 : Math.min(99, placer.percent());
        }

        @Override
        boolean step(long deadline) {
            while (System.nanoTime() < deadline) {
                switch (stage) {
                    case LOAD -> {
                        if (!EstateChunks.hold(level, box)) {
                            if (++waited % 600 == 0) {
                                LOG.warn("Estates: still waiting for the chunks of {} to load", plot);
                            }
                            return false;
                        }
                        stage = Stage.CLEAN;
                    }
                    case CLEAN -> {
                        // a fresh plot holds nothing; anything found (an interrupted earlier build) is kept safe
                        keepAll(EstateContents.takeAll(EstateContents.scan(level, box)), key.owner(), plot);
                        BlockPos origin = EstateLevels.origin(plot);
                        placer = new BlueprintPlacer(level, blueprint, origin, origin.getX(), origin.getZ(), ENTITY_TAG,
                                null, problems -> LOG.error("Estates: {} palette entries of {} are invalid: {}",
                                problems.size(), blueprint.folder(), problems));
                        stage = Stage.PLACE;
                    }
                    case PLACE -> {
                        EstateChunks.hold(level, box);
                        if (!placer.step(deadline)) {
                            return false;
                        }
                        built();
                        return true;
                    }
                }
            }
            return false;
        }

        private void built() {
            Blueprint.Spot entry = blueprint.spot(EstateService.ENTRY_SPOT)
                    .orElse(new Blueprint.Spot(new BlockPos(0, 1, 0), 0.0F));
            BlockPos feet = EstateLevels.at(plot, entry.pos());
            state().markBuilt(plot, key, blueprint.build(), box,
                    new double[]{feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, entry.yaw()});
            LOG.info("Estates: built {} for {} on {} ({} blocks)", blueprint.folder(), id, plot, placer.placed());
            Optional<EstateRecord> record = registry.get(id).filter(r -> r.plot().equals(plot)
                    && r.key().design().equals(key.design()));
            if (record.isEmpty()) {
                return; // released or given up meanwhile: this plot waits to be wiped
            }
            if (record.get().moving()) {
                jobs.addFirst(new MoveJob(id));
            } else {
                onReady.accept(record.get().key());
            }
        }
    }

    /** A redesign's new build stands: the contents move over, the players too, and the old build is wiped. */
    private final class MoveJob extends Job {
        final String id;
        int waited;

        MoveJob(String id) {
            super(registry.get(id).map(EstateRecord::plot).orElse(new EstatePlot(0, 0)));
            this.id = id;
        }

        @Override
        boolean step(long deadline) {
            Optional<EstateRecord> found = registry.get(id).filter(EstateRecord::moving);
            if (found.isEmpty()) {
                return true;
            }
            EstateRecord record = found.get();
            Optional<EstateWorldState.Built> target = builtAs(record.plot(), record.key().design());
            if (target.isEmpty()) {
                queueBuild(record);
                return true;
            }
            AABB to = target.get().aabb();
            AABB from = state().built(record.previous()).map(EstateWorldState.Built::aabb)
                    .orElse(EstateLevels.box(record.previous(), null, level));
            boolean loaded = EstateChunks.hold(level, from) & EstateChunks.hold(level, to);
            if (!loaded) {
                if (++waited % 600 == 0) {
                    LOG.warn("Estates: still waiting for the chunks of {} to load", record.previous());
                }
                return false;
            }
            List<ItemStack> left = EstateContents.move(EstateContents.scan(level, from), EstateContents.scan(level, to));
            keepAll(left, record.key().owner(), record.previous());
            double[] entry = target.get().entry();
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (record.previous().contains(player.getBlockX(), player.getBlockZ())) {
                    player.closeContainer();
                    player.teleportTo(level, entry[0], entry[1], entry[2], (float) entry[3], 0.0F);
                }
            }
            registry.finishMove(id).ifPresent(previous -> queueWipe(previous, record.key().owner()));
            saveIfDirty();
            LOG.info("Estates: {} moved into {} on {}", id, record.key().design(), record.plot());
            onReady.accept(record.key());
            return true;
        }
    }

    /** Clears a plot: what is inside goes to the keepsafe, then every entity and block goes. */
    private final class WipeJob extends Job {
        private enum Stage { LOAD, EVACUATE, CLEAR }

        final UUID owner;
        final AABB box;
        Stage stage = Stage.LOAD;
        int chunk;
        int section = -1;
        int block;
        int waited;

        /** @param hint where a build that the world has no record of may reach (null: anywhere a build could) */
        WipeJob(EstatePlot plot, UUID owner, AABB hint) {
            super(plot);
            this.owner = owner;
            this.box = state().built(plot).map(EstateWorldState.Built::aabb)
                    .orElse(hint != null ? hint : EstateLevels.box(plot, null, level));
        }

        private int chunksX() {
            return ((int) Math.floor(box.maxX - 1.0E-4) >> 4) - ((int) Math.floor(box.minX) >> 4) + 1;
        }

        private int chunksZ() {
            return ((int) Math.floor(box.maxZ - 1.0E-4) >> 4) - ((int) Math.floor(box.minZ) >> 4) + 1;
        }

        @Override
        boolean step(long deadline) {
            switch (stage) {
                case LOAD -> {
                    sendOutOf(plot);
                    if (!EstateChunks.hold(level, box)) {
                        if (++waited % 600 == 0) {
                            LOG.warn("Estates: still waiting for the chunks of {} to load", plot);
                        }
                        return false;
                    }
                    stage = Stage.EVACUATE;
                    return false;
                }
                case EVACUATE -> {
                    keepAll(EstateContents.takeAll(EstateContents.scan(level, box)), owner, plot);
                    for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
                        entity.discard();
                    }
                    stage = Stage.CLEAR;
                    return false;
                }
                case CLEAR -> {
                    EstateChunks.hold(level, box);
                    if (!clear(deadline)) {
                        return false;
                    }
                    state().markWiped(plot);
                    registry.wiped(plot);
                    saveIfDirty();
                    LOG.info("Estates: wiped {}", plot);
                    return true;
                }
            }
            return true;
        }

        /** Clears the non-air blocks of the box, section by section. @return true when done */
        private boolean clear(long deadline) {
            int cx0 = (int) Math.floor(box.minX) >> 4;
            int cz0 = (int) Math.floor(box.minZ) >> 4;
            int width = chunksX();
            int total = width * chunksZ();
            int minY = (int) Math.floor(box.minY);
            int maxY = (int) Math.ceil(box.maxY) - 1;
            BlockState air = Blocks.AIR.defaultBlockState();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            while (chunk < total) {
                LevelChunk levelChunk = level.getChunk(cx0 + chunk % width, cz0 + chunk / width);
                LevelChunkSection[] sections = levelChunk.getSections();
                int minSection = levelChunk.getMinSection();
                if (section < 0) {
                    section = 0;
                    block = 0;
                }
                while (section < sections.length) {
                    int sectionY = (minSection + section) << 4;
                    if (sections[section].hasOnlyAir() || sectionY + 15 < minY || sectionY > maxY) {
                        section++;
                        block = 0;
                        continue;
                    }
                    while (block < 4096) {
                        if ((block & 255) == 0 && System.nanoTime() >= deadline) {
                            return false;
                        }
                        int at = block++;
                        int y = sectionY + (at >> 8);
                        if (y < minY || y > maxY) {
                            continue;
                        }
                        cursor.set(levelChunk.getPos().getMinBlockX() + (at & 15), y,
                                levelChunk.getPos().getMinBlockZ() + ((at >> 4) & 15));
                        if (!level.getBlockState(cursor).isAir()) {
                            level.setBlock(cursor, air, FLAGS);
                        }
                    }
                    section++;
                    block = 0;
                }
                chunk++;
                section = -1;
            }
            return true;
        }
    }

    /** Things that had to leave an estate without a place to go wait in the owner's keepsafe. */
    private void keepAll(List<ItemStack> stacks, UUID owner, EstatePlot plot) {
        if (stacks.isEmpty()) {
            return;
        }
        UUID keeper = owner != null ? owner : new UUID(0L, 0L);
        for (Iterator<ItemStack> it = stacks.iterator(); it.hasNext(); ) {
            state().keep(keeper, it.next());
        }
        LOG.info("Estates: {} stack(s) from {} kept safe for {}", stacks.size(), plot, keeper);
    }
}
