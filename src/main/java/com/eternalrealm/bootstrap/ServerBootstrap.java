package com.eternalrealm.bootstrap;

import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.forge.CharacterSync;
import com.eternalrealm.character.forge.ForgePlayerDataListener;
import com.eternalrealm.combat.forge.CombatDriver;
import com.eternalrealm.config.ERServerConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.core.PeriodicTrigger;
import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.concurrent.MainThreadQueue;
import com.eternalrealm.network.TokenBucketRateLimiter;
import com.eternalrealm.network.ViolationTracker;
import com.eternalrealm.persistence.backup.SnapshotBackupService;
import com.eternalrealm.persistence.file.AtomicFileStore;
import com.eternalrealm.persistence.file.EmergencyDumpWriter;
import com.eternalrealm.persistence.file.FileAccountRepository;
import com.eternalrealm.persistence.file.FileCharacterRepository;
import com.eternalrealm.persistence.file.FileGuildRepository;
import com.eternalrealm.persistence.file.FileHallOfFameRepository;
import com.eternalrealm.persistence.file.FileRankingRepository;
import com.eternalrealm.persistence.file.FileSeasonStateRepository;
import com.eternalrealm.ranking.forge.RankingService;
import com.eternalrealm.social.forge.GuildManager;
import com.eternalrealm.social.forge.PartyManager;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.server.GameServices;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.SyncQueue;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/**
 * Composition root of the server side: builds the {@link ServerContext} when a logical server starts, drives it every
 * tick and flushes it when the server stops. Works identically for dedicated, integrated and GameTest servers.
 */
public final class ServerBootstrap {
    private static final Logger LOG = LogUtils.getLogger();
    static final String DATA_DIRECTORY = "eternalrealm";
    static final String BACKUP_DIRECTORY = "eternalrealm_backups";

    private ServerBootstrap() {
    }

    /** Server configs are loaded by Forge right before this event, so they can be read here. */
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ServerContext stale = ServerContext.uninstall();
        if (stale != null) {
            LOG.warn("A previous server context was still installed; shutting it down first");
            shutdown(stale);
        }
        ServerContext context = create(event.getServer());
        ServerContext.install(context);
        LOG.info("{} server services started: season={}, data={}", ModInfo.DISPLAY_NAME,
                context.playerData().season(), context.dataDirectory());
    }

    private static ServerContext create(MinecraftServer server) {
        Path worldDirectory = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path dataDirectory = worldDirectory.resolve(DATA_DIRECTORY);
        Path backupDirectory = worldDirectory.resolve(BACKUP_DIRECTORY);
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            // Without a writable data directory no progress can be saved: refuse to start rather than lose data.
            throw new IllegalStateException("Cannot create the data directory " + dataDirectory, e);
        }

        Clock clock = Clock.systemUTC();
        SeasonId season = ERServerConfig.seasonId();
        CharacterRules rules = ERServerConfig.characterRules();
        MainThreadQueue mainThread = new MainThreadQueue();
        SyncQueue syncQueue = new SyncQueue();
        SyncQueue combatStatsQueue = new SyncQueue();
        AtomicFileStore store = new AtomicFileStore(dataDirectory.resolve("quarantine"), clock);
        KeyedSerialExecutor<UUID> io = new KeyedSerialExecutor<>(ERServerConfig.IO_THREADS.get(), "EternalRealm-IO");

        PlayerDataService playerData = new PlayerDataService(
                new FileAccountRepository(dataDirectory, store),
                new FileCharacterRepository(dataDirectory, store),
                io, mainThread, clock, season, rules,
                Duration.ofSeconds(ERServerConfig.AUTOSAVE_INTERVAL_SECONDS.get()),
                new ForgePlayerDataListener(server, mainThread, syncQueue, combatStatsQueue),
                new EmergencyDumpWriter(dataDirectory.resolve("emergency"), clock));
        SnapshotBackupService backups = new SnapshotBackupService(dataDirectory, backupDirectory,
                ERServerConfig.BACKUP_KEEP.get(), clock);
        PeriodicTrigger backupTrigger = new PeriodicTrigger(
                Duration.ofMinutes(ERServerConfig.BACKUP_INTERVAL_MINUTES.get()), clock);
        TokenBucketRateLimiter<UUID> allocateLimiter = TokenBucketRateLimiter.systemClock(
                ERServerConfig.ALLOCATE_BURST.get(), ERServerConfig.ALLOCATE_PER_SECOND.get());
        long violationWindowMillis = ERServerConfig.VIOLATION_WINDOW_SECONDS.get() * 1000L;
        ViolationTracker violations = new ViolationTracker(ERServerConfig.VIOLATION_KICK_THRESHOLD.get(),
                violationWindowMillis, System::currentTimeMillis);
        ViolationTracker floods = new ViolationTracker(ERServerConfig.FLOOD_KICK_THRESHOLD.get(),
                violationWindowMillis, System::currentTimeMillis);

        CombatDriver combat = new CombatDriver(server, playerData, combatStatsQueue,
                TokenBucketRateLimiter.systemClock(ERServerConfig.COMBAT_ACTION_BURST.get(),
                        ERServerConfig.COMBAT_ACTION_PER_SECOND.get()),
                ERServerConfig.combatTuning());

        TokenBucketRateLimiter<UUID> uiLimiter = TokenBucketRateLimiter.systemClock(
                ERServerConfig.UI_ACTION_BURST.get(), ERServerConfig.UI_ACTION_PER_SECOND.get());
        RankingService rankings = new RankingService(server, playerData, io, clock,
                new FileRankingRepository(dataDirectory, store), new FileSeasonStateRepository(dataDirectory, store),
                new FileHallOfFameRepository(dataDirectory, store), ERServerConfig.seasonSchedule(clock.instant()));
        com.eternalrealm.world.forge.WorldService world = new com.eternalrealm.world.forge.WorldService(server,
                playerData, io, new com.eternalrealm.persistence.file.FileWorldProgressRepository(dataDirectory, store),
                clock, season);
        world.onDiscover((player, area) -> rankings.onAreaDiscovered(player));
        GameServices game = new GameServices(ERServerConfig.jobRules(), new PartyManager(server, playerData),
                new GuildManager(server, new FileGuildRepository(dataDirectory, store), io, clock, season), rankings,
                world, new com.eternalrealm.quest.forge.QuestService(server, playerData, world, rankings, clock),
                new com.eternalrealm.monster.forge.MonsterSpawner(server),
                new com.eternalrealm.dungeon.forge.DungeonService(server),
                new com.eternalrealm.story.forge.StoryDirector(server, world, playerData),
                new com.eternalrealm.economy.forge.MarketService(server, playerData, io,
                        new com.eternalrealm.persistence.file.FileMarketRepository(dataDirectory, store), clock, season));

        return new ServerContext(server, dataDirectory, mainThread, io, playerData, backups, backupTrigger,
                allocateLimiter, violations, floods, syncQueue, combat, uiLimiter, game);
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        // Each step is isolated: a bug in one subsystem must not stop autosave or the others.
        try {
            context.mainThread().drain();
        } catch (RuntimeException e) {
            LOG.error("Draining server-thread tasks failed", e);
        }
        try {
            context.playerData().tick();
        } catch (RuntimeException e) {
            LOG.error("Player data tick failed", e);
        }
        try {
            CharacterSync.flush(context);
        } catch (RuntimeException e) {
            LOG.error("Character sync failed", e);
        }
        try {
            context.combat().tick();
        } catch (RuntimeException e) {
            LOG.error("Combat tick failed", e);
        }
        try {
            context.game().tick(context.server().getTickCount());
        } catch (RuntimeException e) {
            LOG.error("Game services tick failed", e);
        }
        if (context.backupTrigger().poll()) {
            context.backups().createSnapshotAsync().whenComplete((path, error) -> {
                if (error != null) {
                    LOG.error("Scheduled backup snapshot failed", error);
                }
            });
        }
    }

    /**
     * Queues saves right away so they run in parallel with vanilla's world save (and are more likely to be on disk if
     * the process is killed). The final flush happens in {@link #onServerStopped}.
     */
    public static void onServerStopping(ServerStoppingEvent event) {
        ServerContext.current()
                .filter(context -> context.server() == event.getServer())
                .ifPresent(context -> {
                    try {
                        int queued = context.playerData().saveAllDirty();
                        LOG.info("Server stopping: queued {} character save(s); final flush follows", queued);
                    } catch (RuntimeException e) {
                        LOG.error("Queuing saves on server stop failed", e);
                    }
                });
    }

    /**
     * Final flush. Fired even when the server stops because of a crash. Vanilla does not reliably fire
     * {@code PlayerLoggedOutEvent} for remote players while stopping (their connections are only closed), so the
     * flush saves everything that is still loaded instead of relying on logout saves.
     */
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerContext context = ServerContext.uninstallIfOwnedBy(event.getServer());
        if (context != null) {
            shutdown(context);
        }
    }

    private static void shutdown(ServerContext context) {
        Duration timeout = Duration.ofSeconds(ERServerConfig.SHUTDOWN_FLUSH_TIMEOUT_SECONDS.get());
        try {
            context.game().shutdown();
        } catch (RuntimeException e) {
            LOG.error("Flushing game services failed", e);
        }
        try {
            context.playerData().shutdown(timeout);
        } catch (RuntimeException e) {
            LOG.error("Flushing player data failed", e);
        }
        if (ERServerConfig.BACKUP_ON_SHUTDOWN.get()) {
            try {
                context.backups().createSnapshot();
            } catch (IOException | RuntimeException e) {
                LOG.error("Shutdown backup snapshot failed", e);
            }
        }
        context.backups().close(timeout);
        if (!context.ioExecutor().shutdown(timeout)) {
            LOG.error("Persistence I/O did not finish within {}", timeout);
        }
        LOG.info("{} server services stopped", ModInfo.DISPLAY_NAME);
    }

    /** Mod bus. Fired on the config file watcher thread; the work is moved to the server thread. */
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        ModConfig config = event.getConfig();
        if (!ModInfo.MOD_ID.equals(config.getModId()) || config.getType() != ModConfig.Type.SERVER) {
            return;
        }
        ServerContext.current().ifPresent(context -> context.mainThread().execute(() -> applyReloadedConfig(context)));
    }

    private static void applyReloadedConfig(ServerContext context) {
        try {
            context.playerData().updateRules(ERServerConfig.characterRules());
            context.playerData().setAutosaveInterval(Duration.ofSeconds(ERServerConfig.AUTOSAVE_INTERVAL_SECONDS.get()));
            context.backupTrigger().setInterval(Duration.ofMinutes(ERServerConfig.BACKUP_INTERVAL_MINUTES.get()));
            context.combat().setTuning(ERServerConfig.combatTuning());
            context.game().setJobRules(ERServerConfig.jobRules());
            if (!context.playerData().season().value().equals(ERServerConfig.SEASON_ID.get())) {
                LOG.warn("season.currentSeasonId was changed; it only takes effect after a restart");
            }
            LOG.info("Reloaded {} server config", ModInfo.DISPLAY_NAME);
        } catch (RuntimeException e) {
            LOG.error("Could not apply the reloaded server config", e);
        }
    }
}
