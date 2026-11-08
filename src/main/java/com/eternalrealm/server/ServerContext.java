package com.eternalrealm.server;

import com.eternalrealm.combat.forge.CombatDriver;
import com.eternalrealm.core.PeriodicTrigger;
import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.concurrent.MainThreadQueue;
import com.eternalrealm.network.TokenBucketRateLimiter;
import com.eternalrealm.network.ViolationTracker;
import com.eternalrealm.persistence.backup.SnapshotBackupService;
import com.eternalrealm.player.PlayerDataService;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Services of the running logical server (dedicated, integrated single-player/LAN, or GameTest server).
 *
 * <p>Created by {@code ServerBootstrap} on {@code ServerAboutToStartEvent} and destroyed on {@code ServerStoppedEvent},
 * so no state can leak from one single-player world into the next one opened in the same JVM.
 * Everything except {@link #mainThread()} must only be used on the server thread.</p>
 */
public final class ServerContext {
    private static volatile ServerContext current;

    private final MinecraftServer server;
    private final Path dataDirectory;
    private final MainThreadQueue mainThread;
    private final KeyedSerialExecutor<UUID> ioExecutor;
    private final PlayerDataService playerData;
    private final SnapshotBackupService backups;
    private final PeriodicTrigger backupTrigger;
    private final TokenBucketRateLimiter<UUID> allocateLimiter;
    private final ViolationTracker violations;
    private final ViolationTracker floods;
    private final SyncQueue syncQueue;
    private final CombatDriver combat;
    private final TokenBucketRateLimiter<UUID> uiLimiter;
    private final GameServices game;

    public ServerContext(MinecraftServer server, Path dataDirectory, MainThreadQueue mainThread,
                         KeyedSerialExecutor<UUID> ioExecutor, PlayerDataService playerData,
                         SnapshotBackupService backups, PeriodicTrigger backupTrigger,
                         TokenBucketRateLimiter<UUID> allocateLimiter, ViolationTracker violations,
                         ViolationTracker floods, SyncQueue syncQueue, CombatDriver combat,
                         TokenBucketRateLimiter<UUID> uiLimiter, GameServices game) {
        this.server = Objects.requireNonNull(server, "server");
        this.dataDirectory = Objects.requireNonNull(dataDirectory, "dataDirectory");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.ioExecutor = Objects.requireNonNull(ioExecutor, "ioExecutor");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.backups = Objects.requireNonNull(backups, "backups");
        this.backupTrigger = Objects.requireNonNull(backupTrigger, "backupTrigger");
        this.allocateLimiter = Objects.requireNonNull(allocateLimiter, "allocateLimiter");
        this.violations = Objects.requireNonNull(violations, "violations");
        this.floods = Objects.requireNonNull(floods, "floods");
        this.syncQueue = Objects.requireNonNull(syncQueue, "syncQueue");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.uiLimiter = Objects.requireNonNull(uiLimiter, "uiLimiter");
        this.game = Objects.requireNonNull(game, "game");
    }

    public static Optional<ServerContext> current() {
        return Optional.ofNullable(current);
    }

    /** Bootstrap only. */
    public static synchronized void install(ServerContext context) {
        if (current != null) {
            throw new IllegalStateException("A ServerContext is already installed");
        }
        current = Objects.requireNonNull(context, "context");
    }

    /** Bootstrap only. Removes whatever context is installed. @return it, or {@code null} */
    public static synchronized ServerContext uninstall() {
        ServerContext previous = current;
        current = null;
        return previous;
    }

    /**
     * Bootstrap only. Removes the installed context only if it belongs to {@code server}, so a late stop event of an
     * old server can never tear down the context of a newer one.
     *
     * @return the removed context, or {@code null}
     */
    public static synchronized ServerContext uninstallIfOwnedBy(MinecraftServer server) {
        ServerContext installed = current;
        if (installed == null || installed.server != server) {
            return null;
        }
        current = null;
        return installed;
    }

    public MinecraftServer server() {
        return server;
    }

    public Path dataDirectory() {
        return dataDirectory;
    }

    public MainThreadQueue mainThread() {
        return mainThread;
    }

    public KeyedSerialExecutor<UUID> ioExecutor() {
        return ioExecutor;
    }

    public PlayerDataService playerData() {
        return playerData;
    }

    public SnapshotBackupService backups() {
        return backups;
    }

    public PeriodicTrigger backupTrigger() {
        return backupTrigger;
    }

    public TokenBucketRateLimiter<UUID> allocateLimiter() {
        return allocateLimiter;
    }

    public ViolationTracker violations() {
        return violations;
    }

    /** Counts packets dropped by rate limits; only a sustained flood disconnects. */
    public ViolationTracker floods() {
        return floods;
    }

    public SyncQueue syncQueue() {
        return syncQueue;
    }

    public CombatDriver combat() {
        return combat;
    }

    /** Rate limit of screen requests (journal, menus). */
    public TokenBucketRateLimiter<UUID> uiLimiter() {
        return uiLimiter;
    }

    public GameServices game() {
        return game;
    }
}
