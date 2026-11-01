package com.frostbound.server;

import com.frostbound.FrostboundMod;
import com.frostbound.block.GeneratorCoreBlockEntity;
import com.frostbound.command.FrostboundCommand;
import com.frostbound.config.FrostboundConfig;
import com.frostbound.core.Difficulty;
import com.frostbound.core.city.ActionResult;
import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.City;
import com.frostbound.core.city.Notice;
import com.frostbound.core.city.Tutorial;
import com.frostbound.core.events.EventSystem;
import com.frostbound.core.heat.HeatNetwork;
import com.frostbound.core.save.SaveMigrations;
import com.frostbound.core.util.Msg;
import com.frostbound.core.util.Tree;
import com.frostbound.core.weather.WeatherState;
import com.frostbound.network.Net;
import com.frostbound.save.FrostboundSavedData;
import com.frostbound.save.NbtTree;
import com.frostbound.world.Dims;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server side of FROSTBOUND: owns the city of the running world, advances it every tick, keeps the world
 * in step (time, weather, structures, NPCs), persists it and talks to clients.
 */
@Mod.EventBusSubscriber(modid = FrostboundMod.MOD_ID)
public final class CityService {
    /** Founding settings chosen on the title screen before a world is created (same JVM). */
    public record FoundingSettings(Difficulty difficulty, int dayLength, String name, boolean tutorial) {
    }

    public static volatile FoundingSettings pendingFounding;

    private static MinecraftServer server;
    private static FrostboundSavedData data;
    private static ForgeWorldAccess worldAccess;
    private static final Set<UUID> managing = new HashSet<>();
    private static int ticks;
    private static WeatherState appliedWeather;
    private static int lastDay;
    private static City.Phase lastPhase;
    private static String lastEventKey;
    private static boolean frozen;

    private CityService() {
    }

    // ------------------------------------------------------------------ accessors

    public static City city() {
        return data == null ? null : data.city;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static boolean isFrostboundWorld() {
        return data != null && data.frostboundWorld;
    }

    public static FrostboundSavedData.Phase phase() {
        return data == null ? FrostboundSavedData.Phase.NOT_STARTED : data.phase;
    }

    public static ServerLevel frozenLevel() {
        return server == null ? null : server.getLevel(Dims.FROZEN_WASTES);
    }

    /** Stops advancing the city (self-test). */
    public static void freeze() {
        frozen = true;
    }

    public static void markDirty() {
        if (data != null) {
            data.setDirty();
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        managing.clear();
        ticks = 0;
        appliedWeather = null;
        lastEventKey = null;
        ServerLevel level = frozenLevel();
        if (level == null) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] frozen_wastes dimension is missing");
            return;
        }
        worldAccess = new ForgeWorldAccess(level);
        recoverCorruptFile();
        data = server.overworld().getDataStorage().computeIfAbsent(FrostboundSavedData::load, FrostboundSavedData::create,
                FrostboundSavedData.NAME);
        boolean fbWorld = server.overworld().getBiome(BlockPos.ZERO).is(Dims.FROZEN_VOID);
        if (fbWorld && !data.frostboundWorld) {
            data.frostboundWorld = true;
            if (data.phase == FrostboundSavedData.Phase.NOT_STARTED) {
                data.phase = FrostboundSavedData.Phase.SETUP;
            }
            data.setDirty();
        }
        if (data.cityTag != null && data.city == null) {
            try {
                Tree.clearRepairs();
                data.city = City.load(NbtTree.fromNbt(data.cityTag), worldAccess);
                if (!Tree.repairs().isEmpty()) {
                    FrostboundMod.LOGGER.warn("[FROSTBOUND] city data repaired on load: {}", Tree.repairs());
                }
                data.cityTag = null;
                data.phase = FrostboundSavedData.Phase.RUNNING;
            } catch (SaveMigrations.NewerSaveException e) {
                data.readOnly = true;
                FrostboundMod.LOGGER.error("[FROSTBOUND] {} - the city is kept read-only and will not be saved", e.getMessage());
            } catch (Exception e) {
                FrostboundMod.LOGGER.error("[FROSTBOUND] could not load city data", e);
            }
        }
        City city = city();
        if (city != null) {
            city.setWorld(worldAccess);
            lastDay = city.clock().day();
            lastPhase = city.phase();
            applyGameRules();
        }
        FoundingSettings pending = pendingFounding;
        if (city == null && data.frostboundWorld && data.phase == FrostboundSavedData.Phase.SETUP) {
            if (pending != null) {
                pendingFounding = null;
                found(pending);
            } else if (server.isDedicatedServer()) {
                Difficulty d;
                try {
                    d = Difficulty.valueOf(FrostboundConfig.SERVER_DIFFICULTY.get());
                } catch (IllegalArgumentException e) {
                    d = Difficulty.NORMAL;
                }
                found(new FoundingSettings(d, FrostboundConfig.DEFAULT_DAY_LENGTH.get(), "FROSTBOUND", false));
            }
        }
        FrostboundMod.LOGGER.info("[FROSTBOUND] server started: frostboundWorld={} phase={} city={}", data.frostboundWorld,
                data.phase, city() != null);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CitizenTracker.clear();
        if (data != null && data.city != null) {
            data.setDirty();
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FrostboundCommand.register(event.getDispatcher());
    }

    /** Moves an unreadable city file aside and restores the newest readable backup. */
    private static void recoverCorruptFile() {
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path main = dir.resolve(FrostboundSavedData.NAME + ".dat");
        if (!Files.exists(main) || readable(main.toFile())) {
            return;
        }
        try {
            Path corrupt = dir.resolve(FrostboundSavedData.NAME + ".corrupt-" + System.currentTimeMillis() + ".dat");
            Files.move(main, corrupt, StandardCopyOption.REPLACE_EXISTING);
            FrostboundMod.LOGGER.error("[FROSTBOUND] city file was unreadable; moved to {}", corrupt.getFileName());
            List<Path> candidates = new ArrayList<>();
            candidates.add(dir.resolve(FrostboundSavedData.NAME + ".dat.bak"));
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().startsWith(FrostboundSavedData.NAME + ".day-"))
                        .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                        .forEach(candidates::add);
            }
            for (Path c : candidates) {
                if (Files.exists(c) && readable(c.toFile())) {
                    Files.copy(c, main, StandardCopyOption.REPLACE_EXISTING);
                    FrostboundMod.LOGGER.warn("[FROSTBOUND] restored city data from {}", c.getFileName());
                    return;
                }
            }
        } catch (IOException e) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] backup recovery failed", e);
        }
    }

    /** True when the file is a readable city file (compressed NBT with a data compound). */
    public static boolean isReadableCityFile(File f) {
        return readable(f);
    }

    private static boolean readable(File f) {
        try {
            CompoundTag t = NbtIo.readCompressed(f);
            return t != null && t.contains("data");
        } catch (Exception e) {
            return false;
        }
    }

    private static void dailyBackup(int day) {
        int keep = FrostboundConfig.DAILY_BACKUPS.get();
        if (keep <= 0 || server == null) {
            return;
        }
        Path dir = server.getWorldPath(LevelResource.ROOT).resolve("data");
        Path main = dir.resolve(FrostboundSavedData.NAME + ".dat");
        if (!Files.exists(main)) {
            return;
        }
        try {
            Files.copy(main, dir.resolve(String.format("%s.day-%04d.bak", FrostboundSavedData.NAME, day - 1)),
                    StandardCopyOption.REPLACE_EXISTING);
            List<Path> backups = new ArrayList<>();
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().startsWith(FrostboundSavedData.NAME + ".day-")).forEach(backups::add);
            }
            backups.sort(Comparator.comparing(p -> p.getFileName().toString()));
            while (backups.size() > keep) {
                Files.deleteIfExists(backups.remove(0));
            }
        } catch (IOException e) {
            FrostboundMod.LOGGER.warn("[FROSTBOUND] daily backup failed", e);
        }
    }

    // ------------------------------------------------------------------ founding

    public static void foundFromPlayer(ServerPlayer player, CompoundTag args) {
        if (city() != null || data == null || !data.frostboundWorld) {
            return;
        }
        if (!server.isSingleplayerOwner(player.getGameProfile()) && !player.hasPermissions(2)) {
            sendResult(player, ActionResult.fail("frostbound.reason.no_permission"));
            return;
        }
        Difficulty d;
        try {
            d = Difficulty.valueOf(args.getString("difficulty"));
        } catch (IllegalArgumentException e) {
            d = Difficulty.NORMAL;
        }
        int len = args.contains("dayLength") ? args.getInt("dayLength") : FrostboundConfig.DEFAULT_DAY_LENGTH.get();
        String name = args.getString("name");
        found(new FoundingSettings(d, len, name.isBlank() ? "FROSTBOUND" : name.substring(0, Math.min(32, name.length())),
                !args.contains("tutorial") || args.getBoolean("tutorial")));
    }

    /** Founds the city in the frozen dimension. Also used by commands and the self-test. */
    public static boolean found(FoundingSettings s) {
        if (city() != null || data == null) {
            return false;
        }
        ServerLevel level = frozenLevel();
        if (level == null) {
            return false;
        }
        int gy = worldAccess.groundHeight(0, 0);
        long seed = server.overworld().getSeed() ^ 0x46524F5354424F55L;
        City city = City.found(seed, s.difficulty(), s.dayLength(), s.name(), s.tutorial(), 0, gy, 0, worldAccess);
        StructureBuilder.buildGenerator(level, 0, gy, 0);
        data.city = city;
        data.cityTag = null;
        data.frostboundWorld = true;
        data.phase = FrostboundSavedData.Phase.RUNNING;
        data.setDirty();
        lastDay = city.clock().day();
        lastPhase = city.phase();
        applyGameRules();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sendToPlaza(p);
            sendSync(p);
            CompoundTag open = new CompoundTag();
            open.putString("screen", "close");
            Net.send(p, "open", open);
        }
        FrostboundMod.LOGGER.info("[FROSTBOUND] city '{}' founded ({}, {} min/day) at y={}", s.name(), s.difficulty(), s.dayLength(), gy);
        return true;
    }

    private static void applyGameRules() {
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
    }

    public static BlockPos plaza() {
        City c = city();
        if (c == null) {
            return new BlockPos(0, Math.max(worldAccess == null ? 65 : worldAccess.groundHeight(0, 20), 65), 20);
        }
        return new BlockPos(c.generatorX(), c.generatorY(), c.generatorZ() + 10);
    }

    public static void sendToPlaza(ServerPlayer player) {
        ServerLevel level = frozenLevel();
        if (level == null) {
            return;
        }
        BlockPos p = plaza();
        int y = Math.max(p.getY(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getX(), p.getZ()));
        player.teleportTo(level, p.getX() + 0.5, y, p.getZ() + 0.5, 180f, 10f);
        player.setRespawnPosition(Dims.FROZEN_WASTES, new BlockPos(p.getX(), y, p.getZ()), 180f, true, false);
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || data == null) {
            return;
        }
        City city = data.city;
        if (city == null) {
            return;
        }
        ticks++;
        if (!frozen) {
            boolean nobody = server.getPlayerList().getPlayerCount() == 0;
            int steps = city.clock().stepsForServerTick(city.dayLengthMinutes(), managementPause() || nobody);
            for (int i = 0; i < steps; i++) {
                city.step();
            }
            if (steps > 0) {
                data.setDirty();
            }
        }
        server.overworld().setDayTime(city.clock().minecraftDayTime());
        applyWeather(city);
        for (Notice n : city.drainNotices()) {
            broadcastNotice(n);
        }
        if (city.clock().day() != lastDay) {
            lastDay = city.clock().day();
            dailyBackup(lastDay);
        }
        if (city.phase() != lastPhase) {
            lastPhase = city.phase();
            if (city.phase() == City.Phase.ENDED || city.phase() == City.Phase.FAILED) {
                broadcastSync();
                broadcastOpen("ending");
            }
        }
        EventSystem.Pending pending = city.events().pending();
        String key = pending == null ? null : pending.id() + "@" + pending.start();
        if (key != null && !key.equals(lastEventKey) && FrostboundConfig.AUTO_OPEN_EVENTS.get()) {
            broadcastSync();
            broadcastOpen("event");
        }
        lastEventKey = key;
        if (ticks % 20 == 0) {
            Tutorial tut = city.tutorial();
            if (tut.enabled() && tut.step() == Tutorial.Step.WELCOME && city.clock().step() >= 30) {
                tut.flag("welcome");
                tut.check();
            }
            broadcastSync();
            syncGeneratorBlock(city);
            ServerLevel level = frozenLevel();
            if (level != null) {
                StructureBuilder.tickConstruction(level, city);
            }
        }
        if (ticks % 20 == 10) {
            CitizenTracker.tick(server, city);
        }
    }

    private static boolean managementPause() {
        if (!FrostboundConfig.PAUSE_IN_MANAGEMENT.get() || managing.isEmpty()) {
            return false;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return false;
        }
        for (ServerPlayer p : players) {
            if (!managing.contains(p.getUUID())) {
                return false;
            }
        }
        return true;
    }

    private static void applyWeather(City city) {
        WeatherState w = city.weather().current();
        if (w == appliedWeather) {
            return;
        }
        appliedWeather = w;
        boolean snow = w.snowing();
        server.overworld().setWeatherParameters(snow ? 0 : 1_000_000, snow ? 1_000_000 : 0, snow, w.stormy());
    }

    private static void syncGeneratorBlock(City city) {
        ServerLevel level = frozenLevel();
        if (level == null) {
            return;
        }
        BlockPos core = StructureBuilder.corePos(city.generatorX(), city.generatorY(), city.generatorZ());
        if (!level.isLoaded(core)) {
            return;
        }
        if (level.getBlockEntity(core) instanceof GeneratorCoreBlockEntity be) {
            be.setState(city.generator().level(), city.generator().running(), city.generator().overload());
        }
        StructureBuilder.setGratesLit(level, city.generatorX(), city.generatorY(), city.generatorZ(), city.generator().running());
    }

    /** Runs simulation steps immediately (debug command and self-test). */
    public static void advance(int steps) {
        City city = city();
        if (city == null) {
            return;
        }
        for (int i = 0; i < steps; i++) {
            city.step();
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------ sync

    public static CompoundTag snapshotTag(City city) {
        CompoundTag tag = NbtTree.toNbt(city.snapshot(40));
        tag.putBoolean("managementPaused", managementPause());
        tag.putBoolean("frostbound", true);
        return tag;
    }

    private static CompoundTag setupTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("frostbound", data != null && data.frostboundWorld);
        tag.putString("phase", data == null ? "NOT_STARTED" : data.phase.name());
        tag.putBoolean("setup", true);
        return tag;
    }

    public static void sendSync(ServerPlayer player) {
        City city = city();
        Net.send(player, "sync", city == null ? setupTag() : snapshotTag(city));
    }

    private static void broadcastSync() {
        City city = city();
        if (city == null) {
            return;
        }
        CompoundTag tag = snapshotTag(city);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.send(p, "sync", tag);
        }
    }

    private static void broadcastNotice(Notice n) {
        CompoundTag tag = msgTag(n.msg());
        tag.putString("kind", n.kind().name());
        if (n.sound() != null) {
            tag.putString("sound", n.sound());
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.send(p, "notice", tag);
        }
    }

    private static void broadcastOpen(String screen) {
        CompoundTag tag = new CompoundTag();
        tag.putString("screen", screen);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Net.send(p, "open", tag);
        }
    }

    public static CompoundTag msgTag(Msg msg) {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", msg.key());
        ListTag args = new ListTag();
        for (String a : msg.args()) {
            args.add(StringTag.valueOf(a));
        }
        tag.put("args", args);
        return tag;
    }

    public static void sendResult(ServerPlayer player, ActionResult result) {
        CompoundTag tag = msgTag(result.msg());
        tag.putBoolean("ok", result.ok());
        Net.send(player, "result", tag);
    }

    public static void sendHeatGrid(ServerPlayer player) {
        City city = city();
        if (city == null) {
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("cx", city.generatorX());
        tag.putInt("cz", city.generatorZ());
        tag.putInt("half", HeatNetwork.GRID_HALF);
        tag.putInt("cell", HeatNetwork.GRID_CELL);
        tag.putByteArray("grid", city.heat().grid(city.generatorX() + 0.5, city.generatorZ() + 0.5, city.outsideTemperature(),
                city.modifiers()));
        Net.send(player, "heat_grid", tag);
    }

    public static void sendCitizens(ServerPlayer player, int page) {
        City city = city();
        if (city == null) {
            return;
        }
        Net.send(player, "citizens", NbtTree.toNbt(city.citizenPage(Math.max(0, page), 40)));
    }

    // ------------------------------------------------------------------ player actions

    public static void setManaging(ServerPlayer player, boolean on) {
        if (on) {
            managing.add(player.getUUID());
            City city = city();
            if (city != null) {
                city.tutorial().flag("welcome");
                city.tutorial().flag("management");
                city.tutorial().check();
            }
        } else {
            managing.remove(player.getUUID());
        }
    }

    public static void openGenerator(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("screen", "management");
        tag.putString("tab", "overview");
        Net.send(player, "open", tag);
    }

    public static void openBuildingAt(ServerPlayer player, BlockPos pos) {
        City city = city();
        if (city == null) {
            return;
        }
        for (Building b : city.buildings()) {
            if (b.contains(pos.getX(), pos.getZ())) {
                CompoundTag tag = new CompoundTag();
                tag.putString("screen", "management");
                tag.putString("tab", "building");
                tag.putInt("id", b.id);
                Net.send(player, "open", tag);
                return;
            }
        }
    }

    /** City-wide time controls: the single-player host, or an operator on a server. */
    private static boolean mayControlTime(ServerPlayer player) {
        return server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2);
    }

    public static void cityAction(ServerPlayer player, String op, CompoundTag a) {
        City city = city();
        if (city == null) {
            return;
        }
        if ((op.equals("speed") || op.equals("pause") || op.equals("day_length") || op.equals("endless"))
                && !mayControlTime(player)) {
            sendResult(player, ActionResult.fail("frostbound.reason.no_permission"));
            return;
        }
        ActionResult r = switch (op) {
            case "gen_level" -> city.setGeneratorLevel(a.getInt("level"));
            case "overload" -> city.setOverload(a.getBoolean("on"));
            case "repair" -> city.repairGenerator();
            case "place" -> {
                String type = a.getString("type");
                int rot = a.getInt("rot");
                int x = a.getInt("x");
                int z = a.getInt("z");
                int sx = rot % 2 == 0 ? typeWidth(type) : typeDepth(type);
                int sz = rot % 2 == 0 ? typeDepth(type) : typeWidth(type);
                int y = worldAccess.groundHeight(x + sx / 2, z + sz / 2);
                yield city.placeBuilding(type, x, y, z, rot);
            }
            case "upgrade" -> city.upgradeBuilding(a.getInt("id"));
            case "demolish" -> city.demolishBuilding(a.getInt("id"));
            case "pause_building" -> city.setPaused(a.getInt("id"), a.getBoolean("on"));
            case "workers" -> city.setDesiredWorkers(a.getInt("id"), a.getInt("count"));
            case "law" -> city.enactLaw(a.getString("id"));
            case "research" -> city.startResearch(a.getString("id"));
            case "expedition" -> city.launchExpedition(a.getInt("site"), a.getInt("members"));
            case "event" -> city.answerEvent(a.getInt("choice"));
            case "speed" -> city.setSpeed(a.getInt("speed"));
            case "pause" -> city.setSimPaused(a.getBoolean("on"));
            case "endless" -> city.continueEndless();
            case "tutorial_ack" -> {
                city.tutorial().flag("welcome");
                city.tutorial().check();
                yield null;
            }
            case "tutorial_off" -> {
                city.tutorial().setEnabled(false);
                yield null;
            }
            case "day_length" -> {
                city.setDayLengthMinutes(a.getInt("minutes"));
                yield ActionResult.ok("frostbound.msg.day_length", city.dayLengthMinutes());
            }
            default -> ActionResult.fail("frostbound.reason.unknown_action");
        };
        data.setDirty();
        if (r != null) {
            sendResult(player, r);
        }
        broadcastSync();
    }

    private static int typeWidth(String id) {
        var t = BuildingTypes.get(id);
        return t == null ? 1 : t.width;
    }

    private static int typeDepth(String id) {
        var t = BuildingTypes.get(id);
        return t == null ? 1 : t.depth;
    }

    // ------------------------------------------------------------------ players

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || data == null || !data.frostboundWorld) {
            return;
        }
        if (!player.level().dimension().equals(Dims.FROZEN_WASTES)) {
            sendToPlaza(player);
        }
        sendSync(player);
        if (city() == null && (server.isSingleplayerOwner(player.getGameProfile()) || player.hasPermissions(2))) {
            CompoundTag tag = new CompoundTag();
            tag.putString("screen", "founding");
            Net.send(player, "open", tag);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        managing.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && data != null && data.frostboundWorld
                && !player.level().dimension().equals(Dims.FROZEN_WASTES)) {
            sendToPlaza(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && data != null && data.frostboundWorld
                && event.getTo().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            sendToPlaza(player);
        }
    }

    // ------------------------------------------------------------------ protection

    public static boolean isProtected(LevelAccessor level, BlockPos pos) {
        City city = city();
        if (city == null || !(level instanceof ServerLevel sl) || !sl.dimension().equals(Dims.FROZEN_WASTES)) {
            return false;
        }
        for (Building b : city.buildings()) {
            int top = b.y + b.type().height + b.level + 2;
            if (pos.getX() >= b.x && pos.getX() <= b.maxX() && pos.getZ() >= b.z && pos.getZ() <= b.maxZ()
                    && pos.getY() >= b.y - 1 && pos.getY() <= top) {
                return true;
            }
        }
        return false;
    }

    private static boolean bypass(Player player) {
        return player != null && player.isCreative() && player.hasPermissions(2);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!bypass(event.getPlayer()) && isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Player p = event.getEntity() instanceof Player pl ? pl : null;
        if (!bypass(p) && isProtected(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPortal(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof ServerLevel sl && sl.dimension().equals(Dims.FROZEN_WASTES)) {
            event.setCanceled(true);
        }
    }
}
