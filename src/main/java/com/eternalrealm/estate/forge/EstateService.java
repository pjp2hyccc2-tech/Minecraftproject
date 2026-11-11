package com.eternalrealm.estate.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstatePlot;
import com.eternalrealm.estate.EstateRecord;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.item.forge.Bags;
import com.eternalrealm.persistence.file.AtomicFileStore;
import com.eternalrealm.persistence.file.FileEstateRepository;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.eternalrealm.world.gen.HubSpot;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Estates (docs/modules/estates.md): private places in their own dimension ({@link EstateLevels}) — a player's room
 * at the inn ({@value EstateKey#ROOM}), a guild's house ({@value EstateKey#GUILD}) — built from blueprints
 * ({@code data/eternalrealm/estates/<kind>/<design>/}) on plots far apart ({@link EstatePlot}), entered and left by
 * teleport. Server thread only; every method is safe to call before the estates are loaded (it answers
 * {@link EstateResult#UNAVAILABLE} or nothing).
 *
 * <h2>The contract</h2>
 * <ul>
 *     <li>{@link #claim}: takes a plot and starts building ({@link #status} tells the progress; {@link #onReady}
 *     callbacks run when it stands). One estate per kind and owner. No gold changes hands here.</li>
 *     <li>{@link #enter} / {@link #leave}: in at the design's {@value #ENTRY_SPOT} spot (where the player was is
 *     remembered with the player), out to that point (else the capital's {@value #LOBBY_SPOT} spot, else its
 *     spawn). The way out is the design's {@value #EXIT_SPOT} spot (a door the game handles).</li>
 *     <li>{@link #redesign}: the new design is built on a fresh plot while the old one is still used; then every
 *     stored thing moves over in one tick (storage pieces by name, slot by slot; the rest into free space), players
 *     inside move along and the old build is wiped. A newer blueprint of the same design is applied the same way
 *     (when the estate is next entered).</li>
 *     <li>{@link #release}: the estate is gone and its plot wiped; anything still inside waits in the owner's
 *     {@link #keepsafe}.</li>
 *     <li>{@link #at}: the estate at a position; {@link #registerAccess}: who besides the owner may enter estates of
 *     a kind. Inside the dimension: no breaking or placing (creative operators aside), no fighting between players,
 *     and storage, doors, item frames and armour stands only for those who may be there; anyone in a place they may
 *     not be is sent out.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EstateService {
    private static final Logger LOG = LogUtils.getLogger();
    /** Blueprint spot where players arrive (feet position and yaw). */
    public static final String ENTRY_SPOT = "entry";
    /** Blueprint spot of the way out (a door or a portal). */
    public static final String EXIT_SPOT = "exit";
    /** Capital spot where players come out when they have no return point. */
    public static final String LOBBY_SPOT = "estate.lobby";

    private static final Map<String, EstateAccess> ACCESS = new ConcurrentHashMap<>();
    private static final List<BiConsumer<ServerLevel, EstateKey>> READY = new CopyOnWriteArrayList<>();
    private static EstateManager manager;
    private static boolean lastRain;
    private static boolean lastThunder;

    private EstateService() {
    }

    // ------------------------------------------------------------------ registration (mod construction)

    /** Who besides the owner may enter estates of {@code kind} (one rule per kind; a later one replaces it). */
    public static void registerAccess(String kind, EstateAccess rule) {
        ACCESS.put(kind, rule);
    }

    /** Runs whenever an estate's build (first build or redesign) is complete and can be entered. */
    public static void onReady(BiConsumer<ServerLevel, EstateKey> listener) {
        READY.add(listener);
    }

    // ------------------------------------------------------------------ queries

    /** Whether the estates are running (the dimension exists and the registry loaded). */
    public static boolean available() {
        return manager != null && manager.available;
    }

    /** One line for operators: estates, plots waiting to be wiped, queued work. */
    public static String describe() {
        EstateManager current = manager;
        if (current == null) {
            return "Estates: not running";
        }
        return "Estates: " + current.registry.all().size() + " estate(s), " + current.registry.wiping().size()
                + " plot(s) to wipe, " + current.pendingJobs() + " job(s) queued"
                + (current.available ? "" : " (the data could not be loaded: nothing changes until it is fixed)");
    }

    /** The estates dimension of a server. */
    public static Optional<ServerLevel> level(MinecraftServer server) {
        return EstateLevels.level(server);
    }

    /** The current key (with its design) of the estate of {@code kind} owned by {@code owner}. */
    public static Optional<EstateKey> estate(String kind, UUID owner) {
        return record(kind, owner).map(EstateRecord::key);
    }

    /** Every estate of {@code kind}. */
    public static List<EstateKey> estates(String kind) {
        List<EstateKey> keys = new ArrayList<>();
        if (manager != null) {
            manager.registry.ofKind(kind).forEach(record -> keys.add(record.key()));
        }
        return keys;
    }

    public static EstateStatus status(EstateKey key) {
        return record(key).map(record -> manager.status(record)).orElse(EstateStatus.NONE);
    }

    /** Whether the estate stands and can be entered. */
    public static boolean ready(EstateKey key) {
        return status(key).enterable();
    }

    /** The estate whose plot holds {@code pos} in {@code level} (only in the estates dimension). */
    public static Optional<EstateKey> at(Level level, BlockPos pos) {
        if (manager == null || !EstateLevels.isEstateLevel(level)) {
            return Optional.empty();
        }
        return EstatePlot.at(pos.getX(), pos.getZ()).flatMap(manager.registry::atPlot).map(EstateRecord::key);
    }

    /** The estate a player stands in. */
    public static Optional<EstateKey> at(ServerPlayer player) {
        return at(player.level(), player.blockPosition());
    }

    /** Whether {@code player} may enter and use the estate: its owner, those its kind's rule lets in, operators. */
    public static boolean mayAccess(ServerPlayer player, EstateKey key) {
        if (player.getUUID().equals(key.owner()) || player.isCreative() && player.hasPermissions(2)) {
            return true;
        }
        EstateAccess rule = ACCESS.get(key.kind());
        try {
            return rule != null && rule.mayEnter(player, key);
        } catch (RuntimeException e) {
            LOG.error("Estate access rule of {} failed", key.kind(), e);
            return false;
        }
    }

    /**
     * A named spot of the estate's current design as a world feet position and yaw in the estates dimension (of
     * the build players use: during a redesign the old one's positions are not known, so empty).
     */
    public static Optional<Blueprint.Spot> spot(EstateKey key, String name) {
        return record(key).filter(record -> !record.moving())
                .flatMap(record -> EstateBlueprints.get(manager.server, record.key())
                        .flatMap(blueprint -> blueprint.spot(name))
                        .map(spot -> new Blueprint.Spot(EstateLevels.at(record.plot(), spot.pos()), spot.yaw())));
    }

    /** The world box the estate's build in use occupies. */
    public static Optional<AABB> box(EstateKey key) {
        return record(key).flatMap(record -> manager.liveBuild(record)).map(EstateWorldState.Built::aabb);
    }

    /** What is stored in the estate (containers, armour stands, item frames, the floor); empty while not built. */
    public static Optional<EstateContents.Found> contents(EstateKey key) {
        return box(key).map(box -> EstateContents.scan(manager.level, box));
    }

    /** Copies of what waits in {@code owner}'s keepsafe. */
    public static List<ItemStack> keepsafe(UUID owner) {
        return manager == null ? List.of() : manager.state().keepsafe(owner);
    }

    /**
     * Hands {@code player} what waits in {@code owner}'s keepsafe, as far as their bag has room (the rest stays).
     *
     * @return the number of stacks handed over
     */
    public static int claimKeepsafe(ServerPlayer player, UUID owner) {
        if (manager == null) {
            return 0;
        }
        List<ItemStack> kept = manager.state().keepsafe(owner);
        List<ItemStack> left = new ArrayList<>();
        int given = 0;
        for (ItemStack stack : kept) {
            if (Bags.firstFree(player.getInventory()) >= 0) {
                Bags.give(player, stack);
                given++;
            } else {
                left.add(stack);
            }
        }
        if (given > 0) {
            manager.state().setKeepsafe(owner, left);
        }
        return given;
    }

    // ------------------------------------------------------------------ changes

    /** Claims the estate {@code key} and starts building its design. */
    public static EstateResult claim(EstateKey key) {
        return manager == null ? EstateResult.UNAVAILABLE : manager.claim(key);
    }

    /**
     * Rebuilds the estate as {@code design}. Everything stored is kept (see the class comment); players can use the
     * old build until the new one stands.
     */
    public static EstateResult redesign(EstateKey key, String design) {
        if (manager == null) {
            return EstateResult.UNAVAILABLE;
        }
        if (!EstateKey.isName(design)) {
            return EstateResult.UNKNOWN_DESIGN;
        }
        return record(key).map(record -> manager.move(record, design)).orElse(EstateResult.NOT_CLAIMED);
    }

    /** Gives the estate up: players inside are sent out and its plot is wiped (leftovers into the keepsafe). */
    public static EstateResult release(EstateKey key) {
        if (manager == null) {
            return EstateResult.UNAVAILABLE;
        }
        return record(key).flatMap(record -> manager.release(record)).map(record -> EstateResult.OK)
                .orElse(EstateResult.NOT_CLAIMED);
    }

    /**
     * Teleports {@code player} into the estate (to its entry), remembering where they were. Tells the player when it
     * cannot be done.
     */
    public static EstateResult enter(ServerPlayer player, EstateKey key) {
        if (manager == null || !manager.available) {
            return tell(player, EstateResult.UNAVAILABLE, 0);
        }
        Optional<EstateRecord> found = record(key);
        if (found.isEmpty()) {
            return tell(player, EstateResult.NOT_CLAIMED, 0);
        }
        EstateRecord record = found.get();
        if (!mayAccess(player, record.key())) {
            return tell(player, EstateResult.NOT_ALLOWED, 0);
        }
        Optional<EstateWorldState.Built> live = manager.liveBuild(record);
        if (live.isEmpty()) {
            return tell(player, EstateResult.BUILDING, manager.status(record).percent());
        }
        manager.refreshIfOutdated(record);
        double[] entry = live.get().entry();
        ServerLevel from = player.serverLevel();
        from.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.3,
                0.8, 0.3, 0.05);
        player.stopRiding();
        player.closeContainer();
        EstateReturns.remember(player);
        manager.level.getChunk(((int) Math.floor(entry[0])) >> 4, ((int) Math.floor(entry[2])) >> 4);
        player.fallDistance = 0.0F;
        player.teleportTo(manager.level, entry[0], entry[1], entry[2], (float) entry[3], 0.0F);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        calmWeather(player);
        manager.level.playSound(null, entry[0], entry[1], entry[2], SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS,
                0.7F, 1.0F);
        return EstateResult.OK;
    }

    /**
     * Takes {@code player} out of the estates: back where they entered from, or to the capital when that place is
     * gone. @return false if the player is not inside the estates dimension
     */
    public static boolean leave(ServerPlayer player) {
        if (!EstateLevels.isEstateLevel(player.level())) {
            return false;
        }
        Optional<EstateReturns.Point> back = EstateReturns.get(player).filter(point -> EstateReturns.usable(player, point));
        EstateReturns.clear(player);
        player.stopRiding();
        player.closeContainer();
        player.fallDistance = 0.0F;
        if (back.isPresent()) {
            ServerLevel target = player.server.getLevel(back.get().dimension());
            player.teleportTo(target, back.get().position().x, back.get().position().y, back.get().position().z,
                    back.get().yaw(), back.get().pitch());
            target.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOODEN_DOOR_CLOSE,
                    SoundSource.PLAYERS, 0.6F, 1.1F);
            return true;
        }
        toCapital(player);
        return true;
    }

    /**
     * Takes {@code player} to the capital's estate lobby ({@value #LOBBY_SPOT}; its spawn while the capital has no
     * such spot), forgetting their return point. @return false without the capital's dimension
     */
    public static boolean toCapital(ServerPlayer player) {
        Optional<ServerLevel> astoria = AstoriaTravel.astoria(player.server);
        EstateReturns.clear(player);
        player.stopRiding();
        player.closeContainer();
        if (astoria.isEmpty()) {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
            return false;
        }
        HubLayout layout = Capital.layout(astoria.get());
        BlockPos lobby = layout.named(LOBBY_SPOT).orElse(layout.spawnPoint());
        float yaw = layout.namedYaw(LOBBY_SPOT, layout.yaw(HubSpot.SPAWN));
        AstoriaTravel.teleport(player, astoria.get(), lobby, yaw);
        return true;
    }

    /** The player's estate of a kind, if any (as a key). */
    private static Optional<EstateRecord> record(String kind, UUID owner) {
        return manager == null ? Optional.empty() : manager.registry.get(kind, owner);
    }

    private static Optional<EstateRecord> record(EstateKey key) {
        return key == null ? Optional.empty() : record(key.kind(), key.owner());
    }

    private static EstateResult tell(ServerPlayer player, EstateResult result, int percent) {
        if (!result.ok()) {
            player.displayClientMessage(Component.translatable(result.messageKey(), percent)
                    .withStyle(ChatFormatting.RED), true);
        }
        return result;
    }

    // ------------------------------------------------------------------ the server's life

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        manager = null;
        Optional<ServerLevel> level = EstateLevels.level(server);
        if (level.isEmpty()) {
            LOG.warn("The estates dimension {} is missing: rooms and guild houses are closed", EstateLevels.ESTATES.location());
            return;
        }
        Optional<ServerContext> context = ServerContext.current().filter(c -> c.server() == server);
        Path directory = context.map(ServerContext::dataDirectory)
                .orElse(server.getWorldPath(LevelResource.ROOT).resolve("eternalrealm"));
        AtomicFileStore store = new AtomicFileStore(directory.resolve("quarantine"), Clock.systemUTC());
        manager = new EstateManager(server, level.get(), new FileEstateRepository(directory, store),
                context.map(ServerContext::ioExecutor).orElse(null), key -> {
                    for (BiConsumer<ServerLevel, EstateKey> listener : READY) {
                        try {
                            listener.accept(level.get(), key);
                        } catch (RuntimeException e) {
                            LOG.error("An estate-ready listener failed for {}", key.id(), e);
                        }
                    }
                });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (manager != null && manager.server == event.getServer()) {
            manager.flush();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        manager = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        EstateManager current = manager;
        if (event.phase != TickEvent.Phase.END || current == null || current.server != event.getServer()) {
            return;
        }
        current.tick();
        long now = current.server.getTickCount();
        if (now % 10 == 3) {
            watchPlayers(current);
        }
        ServerLevel overworld = current.server.overworld();
        boolean rain = overworld.isRaining();
        boolean thunder = overworld.isThundering();
        if (rain != lastRain || thunder != lastThunder || now % 100 == 0) {
            lastRain = rain;
            lastThunder = thunder;
            for (ServerPlayer player : current.level.players()) {
                calmWeather(player);
            }
        }
    }

    /** The estates have no weather: their rain and thunder are kept at zero whatever the overworld does. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level
                && EstateLevels.isEstateLevel(level)) {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
        }
    }

    /** Clear skies for a player in the estates (vanilla tells every dimension when the overworld's rain starts). */
    static void calmWeather(ServerPlayer player) {
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0F));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F));
    }

    /**
     * Twice a second: a player in an estate they may not be in (or in no estate at all) is sent out; one who fell
     * out of a build is put back at its entry. Creative operators and spectators may roam.
     */
    private static void watchPlayers(EstateManager current) {
        for (ServerPlayer player : List.copyOf(current.level.players())) {
            if (player.isSpectator() || player.isCreative() && player.hasPermissions(2)) {
                continue;
            }
            Optional<EstateRecord> record = EstatePlot.at(player.getBlockX(), player.getBlockZ())
                    .flatMap(current.registry::atPlot);
            if (record.isEmpty() || !mayAccess(player, record.get().key())) {
                player.displayClientMessage(Component.translatable(EstateResult.NOT_ALLOWED.messageKey())
                        .withStyle(ChatFormatting.RED), true);
                leave(player);
                continue;
            }
            current.liveBuild(record.get()).ifPresent(built -> {
                if (player.getY() < built.box()[1] - 2) {
                    double[] entry = built.entry();
                    player.fallDistance = 0.0F;
                    player.teleportTo(current.level, entry[0], entry[1], entry[2], (float) entry[3], 0.0F);
                }
            });
        }
    }

    /**
     * Logging in inside the estates: a player who may be where they logged out stays (the build must stand);
     * anyone else is taken out as if through the door.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !EstateLevels.isEstateLevel(player.level())) {
            return;
        }
        EstateManager current = manager;
        Optional<EstateRecord> record = current == null ? Optional.empty()
                : EstatePlot.at(player.getBlockX(), player.getBlockZ()).flatMap(current.registry::atPlot);
        boolean stay = record.isPresent() && mayAccess(player, record.get().key())
                && current.liveBuild(record.get()).filter(built -> built.aabb().contains(player.position())).isPresent();
        if (stay) {
            calmWeather(player);
            return;
        }
        leave(player);
    }

    /**
     * Things dropped in an estate never vanish; monsters and summoned mounts do not come in (the void has none of its
     * own).
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !EstateLevels.isEstateLevel(level)) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity instanceof ItemEntity item) {
            item.setUnlimitedLifetime();
        } else if (entity instanceof Enemy || entity instanceof com.eternalrealm.mount.forge.MountEntity) {
            event.setCanceled(true);
        }
    }
}
