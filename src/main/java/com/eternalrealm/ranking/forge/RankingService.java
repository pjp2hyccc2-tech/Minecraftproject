package com.eternalrealm.ranking.forge;

import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.ranking.HallOfFameRepository;
import com.eternalrealm.ranking.RankingBoard;
import com.eternalrealm.ranking.RankingCalculator;
import com.eternalrealm.ranking.RankingRepository;
import com.eternalrealm.ranking.RankingRules;
import com.eternalrealm.ranking.RankingSnapshot;
import com.eternalrealm.ranking.SeasonArchive;
import com.eternalrealm.ranking.SeasonCounter;
import com.eternalrealm.ranking.SeasonStats;
import com.eternalrealm.ranking.SeasonStatsCodec;
import com.eternalrealm.ranking.SeasonStatsTracker;
import com.eternalrealm.ranking.TimeAttackRules;
import com.eternalrealm.ranking.TimeAttackRun;
import com.eternalrealm.ranking.TimeAttackSubmission;
import com.eternalrealm.season.SeasonEvent;
import com.eternalrealm.season.SeasonManager;
import com.eternalrealm.season.SeasonSchedule;
import com.eternalrealm.season.SeasonStateRepository;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Season clock and rankings on the server: drives the {@link SeasonManager} (announcements, freezing, archiving into the
 * hall of fame), keeps every online player's {@link SeasonStatsTracker} (saved inside the character as the
 * {@value #EXTENSION} extension) and the season's {@link RankingBoard} (saved on the I/O thread). Play systems report
 * events through the {@code on...} methods; counts only change while the season is open. Server thread only.
 */
public final class RankingService {
    private static final Logger LOG = LogUtils.getLogger();
    public static final String EXTENSION = "season_stats";
    private static final UUID IO_KEY = new UUID(0L, 0x72616e6bL);
    private static final double MAX_DISTANCE_PER_SECOND = 60.0;

    private final MinecraftServer server;
    private final PlayerDataService playerData;
    private final KeyedSerialExecutor<UUID> io;
    private final Clock clock;
    private final RankingRepository rankings;
    private final SeasonStateRepository seasonStates;
    private final HallOfFameRepository hallOfFame;
    private final TimeAttackRules timeAttackRules = TimeAttackRules.defaults();
    private final SeasonManager season;
    private final RankingBoard board;
    private final boolean available;
    private final Map<UUID, SeasonStatsTracker> trackers = new HashMap<>();
    private final Map<UUID, Long> publishedRevision = new HashMap<>();
    private final Map<UUID, Vec3> lastPosition = new HashMap<>();
    private boolean boardSaveInFlight;
    private boolean seasonSaveInFlight;
    private boolean archiving;

    public RankingService(MinecraftServer server, PlayerDataService playerData, KeyedSerialExecutor<UUID> io, Clock clock,
                          RankingRepository rankings, SeasonStateRepository seasonStates,
                          HallOfFameRepository hallOfFame, SeasonSchedule configured) {
        this.server = Objects.requireNonNull(server, "server");
        this.playerData = Objects.requireNonNull(playerData, "playerData");
        this.io = Objects.requireNonNull(io, "io");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rankings = Objects.requireNonNull(rankings, "rankings");
        this.seasonStates = Objects.requireNonNull(seasonStates, "seasonStates");
        this.hallOfFame = Objects.requireNonNull(hallOfFame, "hallOfFame");
        SeasonManager manager;
        RankingBoard loadedBoard;
        boolean ok;
        try {
            manager = SeasonManager.atStartup(seasonStates.load(), configured);
            Optional<RankingSnapshot> snapshot = rankings.load(manager.seasonId());
            loadedBoard = snapshot.map(s -> RankingBoard.rebuild(s, RankingRules.defaults()))
                    .orElseGet(() -> new RankingBoard(manager.seasonId(), RankingRules.defaults()));
            if (!manager.isRankingOpen() && manager.status() != com.eternalrealm.season.SeasonStatus.PREPARING) {
                loadedBoard.freeze();
            }
            ok = true;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Could not load the season state or rankings; rankings are disabled for this run", e);
            manager = SeasonManager.prepare(configured);
            loadedBoard = new RankingBoard(configured.id(), RankingRules.defaults());
            ok = false;
        }
        this.season = manager;
        this.board = loadedBoard;
        this.available = ok;
        LOG.info("Season {}: {} (day {})", season.seasonId(), season.status().id(), season.displayDay(clock.instant()));
    }

    public SeasonManager season() {
        return season;
    }

    public RankingBoard board() {
        return board;
    }

    // ------------------------------------------------------------------ play events

    private Optional<SeasonStatsTracker> tracker(ServerPlayer player) {
        if (!available || !season.isRankingOpen()) {
            return Optional.empty();
        }
        return Optional.ofNullable(trackers.get(player.getUUID()));
    }

    public void onMonsterKill(ServerPlayer player, boolean elite) {
        tracker(player).ifPresent(t -> t.recordMonsterKill(elite));
    }

    public void onDamageDealt(ServerPlayer player, double damage) {
        tracker(player).ifPresent(t -> t.addDamageDealt(damage));
    }

    public void onDeath(ServerPlayer player) {
        tracker(player).ifPresent(t -> t.add(SeasonCounter.DEATHS, 1));
    }

    public void onBossKill(ServerPlayer player) {
        tracker(player).ifPresent(t -> t.add(SeasonCounter.BOSSES_KILLED, 1));
    }

    public void onDungeonClear(ServerPlayer player, int partySize) {
        tracker(player).ifPresent(t -> t.recordDungeonClear(partySize));
    }

    public void onQuestCompleted(ServerPlayer player) {
        tracker(player).ifPresent(t -> t.add(SeasonCounter.QUESTS_COMPLETED, 1));
    }

    public void onAreaDiscovered(ServerPlayer player) {
        tracker(player).ifPresent(t -> t.add(SeasonCounter.AREAS_DISCOVERED, 1));
    }

    public void onItemCrafted(ServerPlayer player, int count) {
        tracker(player).ifPresent(t -> t.add(SeasonCounter.ITEMS_CRAFTED, Math.max(0, count)));
    }

    public void onPvpResult(ServerPlayer player, boolean won) {
        tracker(player).ifPresent(t -> t.recordPvpResult(won));
    }

    public void onGuildContribution(ServerPlayer player, long points) {
        tracker(player).ifPresent(t -> t.add(SeasonCounter.GUILD_CONTRIBUTION, Math.max(0, points)));
    }

    /** A boss kill for the time attack board; returns what to announce, if anything. */
    public Optional<TimeAttackSubmission> submitTimeAttack(TimeAttackRun run) {
        if (!available || !season.isRankingOpen()) {
            return Optional.empty();
        }
        return Optional.of(board.submitTimeAttack(run, timeAttackRules));
    }

    // ------------------------------------------------------------------ tick

    /** Once per second. */
    public void tickSecond() {
        Instant now = clock.instant();
        if (available) {
            for (SeasonEvent event : season.tick(now)) {
                handle(event, now);
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (!trackers.containsKey(id) && playerData.isActive(id)) {
                trackers.put(id, loadTracker(id));
            }
            SeasonStatsTracker tracker = trackers.get(id);
            if (tracker == null) {
                continue;
            }
            trackDistance(player, tracker);
            playerData.snapshot(id).ifPresent(snapshot -> {
                if (season.isRankingOpen()) {
                    tracker.setProgress(snapshot.level(), playerData.totalExperience(id));
                }
            });
            publish(player, tracker, now);
        }
        saveIfDirty();
    }

    private void trackDistance(ServerPlayer player, SeasonStatsTracker tracker) {
        Vec3 position = player.position();
        Vec3 previous = lastPosition.put(player.getUUID(), position);
        if (previous == null || !season.isRankingOpen() || !available || player.isPassenger() || player.isSpectator()) {
            return;
        }
        double dx = position.x - previous.x;
        double dz = position.z - previous.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance > 0.5 && distance <= MAX_DISTANCE_PER_SECOND) { // teleports are not travel
            tracker.add(SeasonCounter.DISTANCE_TRAVELLED, (long) distance);
        }
    }

    private SeasonStatsTracker loadTracker(UUID id) {
        Optional<String> json = playerData.extension(id, EXTENSION);
        if (json.isEmpty()) {
            return new SeasonStatsTracker();
        }
        try {
            JsonElement element = JsonParser.parseString(json.get());
            return new SeasonStatsTracker(SeasonStatsCodec.decode(element.getAsJsonObject()));
        } catch (JsonParseException | IllegalStateException | CorruptDataException e) {
            LOG.error("Season statistics of {} are unreadable; starting from zero", id, e);
            return new SeasonStatsTracker();
        }
    }

    /** Writes changed statistics into the character (saved with it) and the leaderboards. */
    private void publish(ServerPlayer player, SeasonStatsTracker tracker, Instant now) {
        UUID id = player.getUUID();
        long revision = tracker.revision();
        Long previous = publishedRevision.get(id);
        if (previous != null && previous == revision) {
            return;
        }
        publishedRevision.put(id, revision);
        SeasonStats stats = tracker.snapshot();
        playerData.setExtension(id, EXTENSION, SeasonStatsCodec.encode(stats).toString());
        if (available && season.isRankingOpen()) {
            board.updatePlayer(id, player.getGameProfile().getName(), stats, now.toEpochMilli());
        }
    }

    public void onLogout(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        SeasonStatsTracker tracker = trackers.get(id);
        if (player != null && tracker != null) {
            publish(player, tracker, clock.instant());
        }
        trackers.remove(id);
        publishedRevision.remove(id);
        lastPosition.remove(id);
    }

    /** Guild scores are the sum of the members' season contribution. */
    public void updateGuild(String guildId, String name, List<SeasonStats> memberStats) {
        if (available && season.isRankingOpen()) {
            board.updateGuild(guildId, name, RankingCalculator.guildScore(memberStats), clock.instant().toEpochMilli());
        }
    }

    // ------------------------------------------------------------------ season events

    private void handle(SeasonEvent event, Instant now) {
        event.type().announcementKey().ifPresent(key -> announce(key, event));
        switch (event.type()) {
            case ENDED -> board.freeze();
            case ARCHIVE_REQUIRED -> archive(now);
            default -> {
            }
        }
    }

    private void announce(String key, SeasonEvent event) {
        Component message = Component.translatable(key, event.seasonId().value(), event.day(),
                season.schedule().lengthDays()).withStyle(ChatFormatting.GOLD);
        Component title = Component.translatable("title.eternalrealm.season.day", event.day());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetSubtitleTextPacket(message));
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.6F, 1.0F);
        }
    }

    private void archive(Instant now) {
        if (archiving) {
            return;
        }
        archiving = true;
        SeasonArchive archive = board.archive(now, 100);
        io.submit(IO_KEY, () -> {
            boolean ok;
            try {
                hallOfFame.append(archive);
                ok = true;
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Writing the hall of fame failed; will retry after a restart", e);
                ok = false;
            }
            boolean appended = ok;
            server.execute(() -> {
                archiving = false;
                if (appended) {
                    season.markArchived(clock.instant());
                }
            });
            return null;
        });
    }

    public void recordFinalBossClear() {
        if (available) {
            season.recordFinalBossClear(clock.instant());
        }
    }

    // ------------------------------------------------------------------ persistence

    private void saveIfDirty() {
        if (!available) {
            return;
        }
        if (board.isDirty() && !boardSaveInFlight) {
            RankingSnapshot snapshot = board.snapshot();
            long revision = board.revision();
            boardSaveInFlight = true;
            io.submit(IO_KEY, () -> {
                boolean ok = save(() -> rankings.save(snapshot), "rankings");
                server.execute(() -> {
                    boardSaveInFlight = false;
                    if (ok) {
                        board.markSaved(revision);
                    }
                });
                return null;
            });
        }
        if (season.isDirty() && !seasonSaveInFlight) {
            var state = season.state();
            long revision = season.revision();
            seasonSaveInFlight = true;
            io.submit(IO_KEY, () -> {
                boolean ok = save(() -> seasonStates.save(state), "season state");
                server.execute(() -> {
                    seasonSaveInFlight = false;
                    if (ok) {
                        season.markSaved(revision);
                    }
                });
                return null;
            });
        }
    }

    private interface Write {
        void run() throws DataAccessException;
    }

    private static boolean save(Write write, String what) {
        try {
            write.run();
            return true;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Saving {} failed; will retry", what, e);
            return false;
        }
    }

    /** Shutdown: publish every tracker into the characters and save synchronously. */
    public void flush() {
        Instant now = clock.instant();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SeasonStatsTracker tracker = trackers.get(player.getUUID());
            if (tracker != null) {
                publish(player, tracker, now);
            }
        }
        if (!available) {
            return;
        }
        save(() -> rankings.save(board.snapshot()), "rankings");
        save(() -> seasonStates.save(season.state()), "season state");
    }

    /** Ranking data for the journal page. */
    public JsonObject view(UUID viewer, com.eternalrealm.ranking.RankingCategory category, int top) {
        JsonObject data = new JsonObject();
        data.addProperty("category", category.id());
        data.addProperty("season", season.seasonId().value());
        data.addProperty("day", season.displayDay(clock.instant()));
        data.addProperty("days", season.schedule().lengthDays());
        data.addProperty("status", season.status().id());
        com.google.gson.JsonArray entries = new com.google.gson.JsonArray();
        if (category.scope() == com.eternalrealm.ranking.RankingCategory.Scope.GUILD) {
            board.topGuilds(top).forEach(entry -> entries.add(entry(entry.rank(), entry.name(), entry.score(), false)));
        } else if (category.scope() == com.eternalrealm.ranking.RankingCategory.Scope.PLAYER) {
            board.top(category, top).forEach(entry ->
                    entries.add(entry(entry.rank(), entry.name(), entry.score(), entry.id().equals(viewer))));
            board.entryOf(category, viewer).ifPresent(own ->
                    data.add("own", entry(own.rank(), own.name(), own.score(), true)));
        }
        data.add("entries", entries);
        return data;
    }

    private static JsonObject entry(int rank, String name, long score, boolean self) {
        JsonObject entry = new JsonObject();
        entry.addProperty("rank", rank);
        entry.addProperty("name", name);
        entry.addProperty("score", score);
        entry.addProperty("self", self);
        return entry;
    }
}
