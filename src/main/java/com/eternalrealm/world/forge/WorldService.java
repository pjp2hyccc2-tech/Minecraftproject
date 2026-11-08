package com.eternalrealm.world.forge;

import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.season.SeasonId;
import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.AreaTracker;
import com.eternalrealm.world.AreaTransition;
import com.eternalrealm.world.BossDefeatResult;
import com.eternalrealm.world.Discovery;
import com.eternalrealm.world.DiscoveryCodec;
import com.eternalrealm.world.WorldMessages;
import com.eternalrealm.world.WorldProgress;
import com.eternalrealm.world.WorldProgressRecord;
import com.eternalrealm.world.WorldProgressRepository;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * The world of a season on the server: which areas are open (bosses unlock the next one), where every player is
 * (title and recommended level on entering an area, discovery for rankings and quests) and a push back from areas
 * that are still sealed. The progress is season data saved through the I/O executor.
 */
public final class WorldService {
    private static final Logger LOG = LoggerFactory.getLogger(WorldService.class);
    private static final UUID IO_KEY = new UUID(0L, 0x776f726c64L);
    /** Player extension holding the discovered areas. */
    public static final String DISCOVERY = "discovery";

    private static final com.eternalrealm.story.StoryCatalog STORY = com.eternalrealm.story.StoryCatalog.standard();

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final KeyedSerialExecutor<UUID> io;
    private final WorldProgressRepository repository;
    private final Clock clock;
    private final AreaTracker tracker = new AreaTracker(Realm.AREAS, AreaTracker.DEFAULT_HYSTERESIS_BLOCKS);
    private final Map<UUID, Vec3> lastAllowed = new HashMap<>();
    private final List<BiConsumer<ServerPlayer, AreaDefinition>> enterListeners = new ArrayList<>();
    private final List<BiConsumer<ServerPlayer, AreaDefinition>> discoverListeners = new ArrayList<>();
    private WorldProgress progress;
    private boolean loaded;
    private boolean saveInFlight;

    public WorldService(MinecraftServer server, PlayerDataService playerData, KeyedSerialExecutor<UUID> io,
                        WorldProgressRepository repository, Clock clock, SeasonId season) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.io = Objects.requireNonNull(io, "io");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.progress = WorldProgress.fresh(season);
        io.submit(IO_KEY, () -> {
            Optional<WorldProgressRecord> record;
            try {
                record = repository.load(season);
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Could not load the world progress of {}; areas stay as at season start and nothing is saved",
                        season.value(), e);
                return null;
            }
            server.execute(() -> {
                record.ifPresent(stored -> progress = WorldProgress.fromRecord(stored));
                loaded = true;
            });
            return null;
        });
    }

    public WorldProgress progress() {
        return progress;
    }

    public boolean isUnlocked(int areaId) {
        return progress.isAreaUnlocked(areaId);
    }

    /** Called when a player enters an area (also on login). */
    public void onEnter(BiConsumer<ServerPlayer, AreaDefinition> listener) {
        enterListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Called when a player sees an area for the first time this season. */
    public void onDiscover(BiConsumer<ServerPlayer, AreaDefinition> listener) {
        discoverListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public Optional<AreaDefinition> areaOf(ServerPlayer player) {
        return tracker.currentArea(player.getUUID());
    }

    // ------------------------------------------------------------------ ticking

    public void tickSecond() {
        ServerLevel level = Realm.level(server);
        BlockPos center = Realm.center(level);
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || !playerData.isActive(player.getUUID())) {
                continue;
            }
            Optional<AreaTransition> transition = tracker.update(player.getUUID(), player.getX() - center.getX(),
                    player.getZ() - center.getZ());
            AreaDefinition area = tracker.currentArea(player.getUUID()).orElse(null);
            if (area == null) {
                continue;
            }
            if (!progress.isAreaUnlocked(area.id()) && !player.isCreative()) {
                pushBack(player, area);
                continue;
            }
            lastAllowed.put(player.getUUID(), player.position());
            transition.ifPresent(change -> entered(player, change.area()));
        }
        saveIfDirty();
    }

    private void entered(ServerPlayer player, AreaDefinition area) {
        TextColor color = TextColor.fromRgb(area.color());
        java.util.Optional<com.eternalrealm.story.AreaCard> card = STORY.areaCard(area.id());
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
        player.connection.send(new ClientboundSetSubtitleTextPacket(card
                .map(c -> (Component) Component.translatable(c.subtitleKey()).withStyle(ChatFormatting.GRAY))
                .orElseGet(() -> Component.translatable(WorldMessages.RECOMMENDED_LEVEL, area.minLevel(), area.maxLevel())
                        .withStyle(ChatFormatting.GRAY))));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(card
                .map(com.eternalrealm.story.AreaCard::titleKey).orElse(area.translationKey()))
                .withStyle(style -> style.withColor(color))));
        player.displayClientMessage(Component.translatable(WorldMessages.RECOMMENDED_LEVEL, area.minLevel(),
                area.maxLevel()).withStyle(ChatFormatting.GRAY), true);
        for (BiConsumer<ServerPlayer, AreaDefinition> listener : enterListeners) {
            listener.accept(player, area);
        }
        Discovery discovery = discovery(player.getUUID());
        if (!discovery.contains(area.id())) {
            Discovery updated = discovery.with(area.id());
            playerData.setExtension(player.getUUID(), DISCOVERY, DiscoveryCodec.toJson(updated).toString());
            player.sendSystemMessage(Component.translatable(WorldMessages.AREA_DISCOVERED,
                    Component.translatable(area.translationKey()).withStyle(style -> style.withColor(color)))
                    .withStyle(ChatFormatting.AQUA));
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 1.0F, 1.0F);
            for (BiConsumer<ServerPlayer, AreaDefinition> listener : discoverListeners) {
                listener.accept(player, area);
            }
        }
    }

    private void pushBack(ServerPlayer player, AreaDefinition area) {
        Vec3 back = lastAllowed.get(player.getUUID());
        if (back == null) {
            BlockPos center = Realm.center(player.serverLevel());
            int y = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.getX(), center.getZ());
            back = new Vec3(center.getX() + 0.5, y, center.getZ() + 0.5);
        }
        player.teleportTo(player.serverLevel(), back.x, back.y, back.z, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.translatable(WorldMessages.AREA_LOCKED,
                Component.translatable(area.translationKey())).withStyle(ChatFormatting.RED), true);
        tracker.forget(player.getUUID());
    }

    public Discovery discovery(UUID playerId) {
        return playerData.extension(playerId, DISCOVERY).map(json -> {
            try {
                return DiscoveryCodec.fromJson(JsonParser.parseString(json));
            } catch (Exception e) {
                LOG.warn("Ignoring unreadable discovery data of {}", playerId, e);
                return Discovery.NONE;
            }
        }).orElse(Discovery.NONE);
    }

    // ------------------------------------------------------------------ bosses

    /** A boss fell: records it, announces the first defeat and every area it opens. */
    public BossDefeatResult onBossDefeated(String bossId) {
        BossDefeatResult result = progress.recordBossDefeat(bossId, clock.instant());
        if (result.firstDefeat()) {
            broadcast(Component.translatable(WorldMessages.BOSS_FIRST_DEFEAT,
                    Component.translatable("boss.eternalrealm." + bossId)).withStyle(ChatFormatting.GOLD));
        }
        for (AreaDefinition area : result.newlyUnlockedAreas()) {
            broadcast(Component.translatable(WorldMessages.AREA_UNLOCKED, Component.translatable(area.translationKey())
                    .withStyle(style -> style.withColor(TextColor.fromRgb(area.color())))).withStyle(ChatFormatting.AQUA));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
            }
        }
        if (result.finalBossNewlyUnlocked()) {
            broadcast(Component.translatable(WorldMessages.FINAL_BOSS_UNLOCKED).withStyle(ChatFormatting.DARK_PURPLE));
        }
        saveIfDirty();
        return result;
    }

    private void broadcast(Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public void onLogout(UUID playerId) {
        tracker.forget(playerId);
        lastAllowed.remove(playerId);
    }

    // ------------------------------------------------------------------ persistence

    private void saveIfDirty() {
        if (!loaded || saveInFlight || !progress.isDirty()) {
            return;
        }
        WorldProgressRecord record = progress.toRecord();
        long revision = progress.revision();
        WorldProgress saving = progress;
        saveInFlight = true;
        io.submit(IO_KEY, () -> {
            boolean ok;
            try {
                repository.save(record);
                ok = true;
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Saving the world progress failed; will retry", e);
                ok = false;
            }
            boolean saved = ok;
            server.execute(() -> {
                saveInFlight = false;
                if (saved) {
                    saving.markSaved(revision);
                }
            });
            return null;
        });
    }

    /** Server stopping: the last state is written synchronously. */
    public void flush() {
        if (!loaded || !progress.isDirty()) {
            return;
        }
        try {
            repository.save(progress.toRecord());
            progress.markSaved(progress.revision());
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Could not save the world progress at shutdown", e);
        }
    }
}
