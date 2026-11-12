package com.eternalrealm.npc.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.npc.Travellers;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.story.DialogueLine;
import com.eternalrealm.world.field.Habitats;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * The travelling adventurers' lives (docs/modules/travellers.md): only in a single-player world (the integrated server,
 * not opened to the network, with nobody but its owner), each goes its round: some minutes hunting at a habitat (most
 * often one near the player), then the item shop, the inn, and out again. They are only there in body while the player
 * is near where they are; elsewhere they just keep to their schedule, which is saved with the world. The first time the
 * player comes close to one, they meet (the meeting lines, a notice counting the travellers met); after that they can
 * talk to them, and in a fight they hear their calls.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TravellerDirector {
    /** Who a player has met (character extension: "bold,sele"). */
    public static final String MET = "travellers_met";
    private static final Logger LOG = LogUtils.getLogger();
    private static final int THINK_TICKS = 20;
    /** A traveller is there in body when the player comes this close to where it is (blocks). */
    private static final double APPEAR = 56.0;
    /** ... and gone again when the player is this far. */
    private static final double VANISH = 104.0;
    /** The player meets a traveller this close (and in sight). */
    private static final double MEET = 7.0;
    /** Its calls in a fight reach players this close. */
    private static final double CALL = 24.0;
    private static final int CALL_GAP_TICKS = 20 * 20;
    private static final int HUB_REFRESH_TICKS = 20 * 60 * 5;

    private static final Map<Travellers.Traveller, TravellerEntity> BODIES = new EnumMap<>(Travellers.Traveller.class);
    private static final Map<Travellers.Traveller, Long> LAST_CALL = new EnumMap<>(Travellers.Traveller.class);
    private static final Random RANDOM = new Random();
    private static MinecraftServer owner;
    private static HubLayout hub;
    private static long hubAt;
    private static volatile boolean forced;

    private TravellerDirector() {
    }

    /** Tests: let the travellers live on a server that is not a single-player world. */
    public static void forceForTests(boolean on) {
        forced = on;
    }

    /**
     * Whether the travellers live on this server: a single-player world (the integrated server, not opened to LAN)
     * with nobody but its owner online. Anybody else (a LAN guest, a test bot) makes it a multiplayer world, where
     * they do not appear.
     */
    public static boolean allowed(MinecraftServer server) {
        if (forced) {
            return true;
        }
        if (!server.isSingleplayer() || server.isPublished()) {
            return false;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        return players.size() == 1 && server.isSingleplayerOwner(players.get(0).getGameProfile());
    }

    // ------------------------------------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        if (owner != server) {
            owner = server;
            BODIES.clear();
            LAST_CALL.clear();
            hub = null;
            TravellerSkills.clear();
        }
        TravellerSkills.tick();
        if (server.getTickCount() % THINK_TICKS != 0) {
            return;
        }
        try {
            think(server);
        } catch (RuntimeException e) {
            LOG.error("The travelling adventurers stumbled; they rest a while", e);
            dismissAll(false);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BODIES.clear();
        TravellerSkills.clear();
    }

    private static void think(MinecraftServer server) {
        ServerLevel realm = AstoriaTravel.astoria(server).orElse(null);
        if (realm == null || !allowed(server)) {
            dismissAll(false);
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        ServerPlayer host = players.isEmpty() ? null : players.get(0);
        TravellerWorldState state = TravellerWorldState.get(realm);
        long now = realm.getGameTime();
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            TravellerEntity body = BODIES.get(traveller);
            if (body != null && (body.isRemoved() || body.level() != realm)) {
                BODIES.remove(traveller);
                body = null;
            }
            Optional<Travellers.Plan> current = state.plan(traveller);
            if (current.isEmpty() || now >= current.get().until()) {
                if (body != null && current.isPresent() && (body.enemy() != null || body.isTalking())) {
                    // never in the middle of a fight or a talk: a little later
                    Travellers.Plan later = new Travellers.Plan(current.get().place(), current.get().habitat(),
                            now + 200);
                    state.set(traveller, later);
                } else {
                    state.set(traveller, nextPlan(traveller, current.orElse(null), host, realm, now));
                    if (body != null) {
                        TravellerSkills.trip(body, false);
                        body.discard();
                        BODIES.remove(traveller);
                        body = null;
                    }
                }
            }
            Travellers.Plan plan = state.plan(traveller).orElseThrow();
            Optional<Vec3> center = center(realm, plan);
            if (center.isEmpty()) {
                state.set(traveller, nextPlan(traveller, plan, host, realm, now)); // its habitat is gone
                continue;
            }
            boolean hostHere = host != null && host.level() == realm && host.isAlive();
            double distance = hostHere ? horizontal(host.position(), body != null ? body.position() : center.get())
                    : Double.MAX_VALUE;
            if (body == null && distance <= APPEAR) {
                body = appear(realm, traveller, plan, center.get(), host);
            } else if (body != null && distance > VANISH) {
                body.discard();
                BODIES.remove(traveller);
                body = null;
            }
            if (body != null && hostHere) {
                notice(host, body);
            }
        }
    }

    private static void dismissAll(boolean visibly) {
        for (TravellerEntity body : BODIES.values()) {
            if (visibly) {
                TravellerSkills.trip(body, false);
            }
            body.discard();
        }
        BODIES.clear();
    }

    /** The next stop of a traveller's round (a habitat near the player most of the time). */
    private static Travellers.Plan nextPlan(Travellers.Traveller traveller, Travellers.Plan finished, ServerPlayer host,
                                           ServerLevel realm, long now) {
        Travellers.Place place = finished == null
                ? Travellers.Place.values()[RANDOM.nextInt(Travellers.Place.values().length)]
                : Travellers.nextPlace(finished.place(), RANDOM.nextDouble());
        long stay = Travellers.stay(place, realm.isNight(), RANDOM.nextDouble());
        if (place == Travellers.Place.FIELD) {
            double px = host != null && host.level() == realm ? host.getX() : 0.0;
            double pz = host != null && host.level() == realm ? host.getZ() : 0.0;
            String last = finished == null ? "" : finished.habitat();
            Optional<Habitats.Habitat> habitat = Travellers.pickHabitat(Habitats.all(), last, px, pz, RANDOM);
            if (habitat.isPresent()) {
                return new Travellers.Plan(place, habitat.get().id(), now + stay);
            }
            place = Travellers.Place.INN; // no habitats in this world: the inn
            stay = Travellers.stay(place, realm.isNight(), RANDOM.nextDouble());
        }
        return new Travellers.Plan(place, "", now + stay);
    }

    // ------------------------------------------------------------------------------------------------ places

    private static Optional<HubLayout> hub(ServerLevel realm) {
        long now = realm.getServer().getTickCount();
        if (hub == null || now - hubAt > HUB_REFRESH_TICKS) {
            hub = Capital.layout(realm);
            hubAt = now;
        }
        return Optional.ofNullable(hub);
    }

    /** Where the traveller of {@code plan} is (its height may be rough until the ground there is loaded). */
    private static Optional<Vec3> center(ServerLevel realm, Travellers.Plan plan) {
        return switch (plan.place()) {
            case FIELD -> Habitats.byId(plan.habitat()).map(h -> new Vec3(h.x() + 0.5, 0.0, h.z() + 0.5));
            case SHOP -> hub(realm).map(layout -> Vec3.atBottomCenterOf(layout.itemShop()));
            case INN -> hub(realm).flatMap(layout -> layout.named("npc.innkeeper")).map(Vec3::atBottomCenterOf);
        };
    }

    /** The traveller comes into the world where it is (with a waystone's swirl if the player is close). */
    private static TravellerEntity appear(ServerLevel realm, Travellers.Traveller traveller, Travellers.Plan plan,
                                         Vec3 center, ServerPlayer host) {
        BlockPos rough = BlockPos.containing(center);
        if (!realm.hasChunkAt(rough)) {
            return null;
        }
        Optional<Vec3> spot;
        double roam;
        if (plan.place() == Travellers.Place.FIELD) {
            Habitats.Habitat habitat = Habitats.byId(plan.habitat()).orElseThrow();
            roam = Math.max(6.0, habitat.radius());
            spot = fieldSpot(realm, center, roam * 0.5);
        } else {
            roam = 1.5;
            spot = townSpot(realm, rough, traveller.ordinal());
        }
        if (spot.isEmpty()) {
            return null;
        }
        TravellerEntity body = NpcEntities.TRAVELLER.get().create(realm);
        if (body == null) {
            return null;
        }
        Vec3 at = spot.get();
        Vec3 anchor = plan.place() == Travellers.Place.FIELD
                ? new Vec3(center.x, at.y, center.z) : at;
        body.moveTo(at.x, at.y, at.z, RANDOM.nextFloat() * 360.0F, 0.0F);
        body.setup(traveller, plan.place(), anchor, roam);
        realm.addFreshEntity(body);
        BODIES.put(traveller, body);
        if (host != null && host.distanceTo(body) < CALL) {
            TravellerSkills.trip(body, true);
        }
        return body;
    }

    /** A dry place on the ground of the habitat. */
    private static Optional<Vec3> fieldSpot(ServerLevel realm, Vec3 center, double radius) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double distance = RANDOM.nextDouble() * radius;
            int x = (int) Math.floor(center.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(center.z + Math.sin(angle) * distance);
            BlockPos feet = new BlockPos(x, realm.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (realm.hasChunkAt(feet) && fits(realm, feet) && realm.getFluidState(feet.below()).isEmpty()) {
                return Optional.of(Vec3.atBottomCenterOf(feet));
            }
        }
        return Optional.empty();
    }

    /** A free place two to four blocks from {@code npc} (each traveller starts looking on another side). */
    private static Optional<Vec3> townSpot(ServerLevel realm, BlockPos npc, int turn) {
        for (int r = 2; r <= 4; r++) {
            for (int i = 0; i < 8; i++) {
                double angle = (i + turn * 2) * Math.PI / 4.0;
                int dx = (int) Math.round(Math.cos(angle) * r);
                int dz = (int) Math.round(Math.sin(angle) * r);
                for (int dy : new int[]{0, 1, -1}) {
                    BlockPos feet = npc.offset(dx, dy, dz);
                    if (realm.hasChunkAt(feet) && fits(realm, feet)) {
                        return Optional.of(Vec3.atBottomCenterOf(feet));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean fits(ServerLevel realm, BlockPos feet) {
        return realm.getBlockState(feet).getCollisionShape(realm, feet).isEmpty()
                && realm.getBlockState(feet.above()).getCollisionShape(realm, feet.above()).isEmpty()
                && !realm.getBlockState(feet.below()).getCollisionShape(realm, feet.below()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------- meeting, talk

    /** The player comes close: the first time, they meet. */
    private static void notice(ServerPlayer player, TravellerEntity body) {
        if (player.distanceTo(body) > MEET || !player.hasLineOfSight(body)) {
            return;
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || !context.playerData().isActive(player.getUUID())) {
            return;
        }
        Set<Travellers.Traveller> met = Travellers.parseMet(context.playerData().extension(player.getUUID(), MET)
                .orElse(""));
        if (!met.contains(body.who())) {
            meet(context, player, body, met);
        }
    }

    private static void meet(ServerContext context, ServerPlayer player, TravellerEntity body,
                             Set<Travellers.Traveller> met) {
        met.add(body.who());
        context.playerData().setExtension(player.getUUID(), MET, Travellers.formatMet(met));
        body.listenTo(player);
        List<DialogueLine> lines = Travellers.meetKeys(body.who()).stream()
                .map(key -> new DialogueLine(body.who().nameKey(), key)).toList();
        context.game().story().play(player, lines, false, null);
        player.sendSystemMessage(Component.translatable("message.eternalrealm.traveller.met",
                Component.translatable(body.who().nameKey()).withStyle(ChatFormatting.AQUA), met.size(),
                Travellers.Traveller.values().length).withStyle(ChatFormatting.GOLD));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
    }

    /** The player talks to a traveller (right click): a line for where they are (or the meeting). */
    static void talk(ServerPlayer player, TravellerEntity body) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || body.isTalking()) {
            return;
        }
        Set<Travellers.Traveller> met = Travellers.parseMet(context.playerData().extension(player.getUUID(), MET)
                .orElse(""));
        if (!met.contains(body.who())) {
            meet(context, player, body, met);
            return;
        }
        body.listenTo(player);
        player.playNotifySound(com.eternalrealm.sound.ERSounds.UI_NPC_TALK.get(), SoundSource.NEUTRAL, 0.7F,
                0.95F + body.getRandom().nextFloat() * 0.1F);
        int pick = body.getRandom().nextInt(Travellers.LINES_PER_PLACE);
        String line = Travellers.talkKey(body.who(), body.place(), pick);
        context.game().story().play(player, List.of(new DialogueLine(body.who().nameKey(), line)), false, null);
    }

    /** Its call when it uses a skill, to the players near (now and then, not at every skill). */
    static void shout(TravellerEntity body, Travellers.Skill skill) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || !(body.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getServer().getTickCount();
        Long last = LAST_CALL.get(body.who());
        if (last != null && now - last < CALL_GAP_TICKS) {
            return;
        }
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, body.getBoundingBox().inflate(CALL),
                player -> !player.isSpectator());
        if (near.isEmpty()) {
            return;
        }
        LAST_CALL.put(body.who(), now);
        context.game().story().bark(near, new DialogueLine(body.who().nameKey(), skill.shoutKey()));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
