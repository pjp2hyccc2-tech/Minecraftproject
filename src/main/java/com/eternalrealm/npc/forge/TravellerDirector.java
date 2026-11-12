package com.eternalrealm.npc.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.npc.Travellers;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.story.DialogueLine;
import com.eternalrealm.world.field.Habitats;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The legendary travellers' lives (docs/modules/travellers.md), only in a single-player world (the integrated server,
 * not opened to the network, with nobody but its owner).
 *
 * <p>On their own, each goes its round: some minutes hunting at a habitat (most often one near the player), then the
 * item shop, the inn, and out again; they are only there in body while the player is near where they are. The first
 * time the player comes close to one, they meet (the meeting lines and the question whether to travel together);
 * after that, talking to them asks again. A traveller who said yes is the player's companion (stored with the
 * character: {@link #PARTY}): it stays at the player's side wherever they go, across worlds and logins, and shows in
 * the party frames, until the player parts with them (talking to them, or the party page).</p>
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TravellerDirector {
    /** Who a player has met (character extension: "rosalind,mariel"). */
    public static final String MET = "travellers_met";
    /** Who travels with the player (character extension: "rosalind,mariel"). */
    public static final String PARTY = "travellers_party";
    private static final Logger LOG = LogUtils.getLogger();
    private static final int THINK_TICKS = 20;
    /** A traveller on its round is there in body when the player comes this close to where it is (blocks). */
    private static final double APPEAR = 56.0;
    /** ... and gone again when the player is this far. */
    private static final double VANISH = 104.0;
    /** The player meets a traveller this close (and in sight). */
    private static final double MEET = 7.0;
    /** Its calls reach players this close. */
    private static final double CALL = 32.0;
    private static final int CALL_GAP_TICKS = 20 * 12;
    private static final int HUB_REFRESH_TICKS = 20 * 60 * 5;
    /** A companion this far from the player (same world) is brought over by the director itself. */
    private static final double FAR = 48.0;
    /** The party frames: the legends stand at the level cap. */
    private static final int HUD_LEVEL = Travellers.LEVEL;

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
            TravellerMoves.clear();
        }
        TravellerMoves.tick();
        if (server.getTickCount() % THINK_TICKS != 0) {
            return;
        }
        try {
            think(server);
        } catch (RuntimeException e) {
            LOG.error("The travellers stumbled; they rest a while", e);
            dismissAll(false);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        BODIES.clear();
        TravellerMoves.clear();
    }

    private static void think(MinecraftServer server) {
        if (!allowed(server)) {
            dismissAll(false);
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        ServerPlayer host = players.isEmpty() ? null : players.get(0);
        ServerContext context = ServerContext.current().orElse(null);
        Set<Travellers.Traveller> party = host == null || context == null
                || !context.playerData().isActive(host.getUUID()) ? Set.of() : partyOf(context, host);
        ServerLevel realm = AstoriaTravel.astoria(server).orElse(null);
        TravellerWorldState state = realm == null ? null : TravellerWorldState.get(realm);
        long now = realm == null ? server.getTickCount() : realm.getGameTime();
        int index = 0;
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            TravellerEntity body = BODIES.get(traveller);
            if (body != null && body.isRemoved()) {
                BODIES.remove(traveller);
                body = null;
            }
            if (party.contains(traveller)) {
                keepCompany(traveller, body, host, index++);
                continue;
            }
            if (body != null && body.isCompanion()) {
                body.discard(); // parted with the player
                BODIES.remove(traveller);
                body = null;
            }
            if (realm == null || state == null) {
                continue;
            }
            round(traveller, body, host, realm, state, now);
        }
    }

    /** A companion: at the player's side, in the player's world. */
    private static void keepCompany(Travellers.Traveller traveller, @Nullable TravellerEntity body, ServerPlayer host,
                                    int index) {
        if (host.isSpectator() || !(host.level() instanceof ServerLevel level)) {
            return;
        }
        if (body != null && (body.level() != level || !body.isCompanion())) {
            body.discard();
            BODIES.remove(traveller);
            body = null;
        }
        if (body == null) {
            body = arriveAt(level, traveller, host, index);
            return;
        }
        if (body.formationSlot() != index) {
            body.becomeCompanion(host, index);
        }
        if (body.distanceTo(host) > FAR && !body.isDown()) {
            body.catchUp(host); // left far behind (its ground stopped ticking): it steps over to the player
        }
    }

    /** Brings {@code traveller} to the player's side (a new body). */
    @Nullable
    private static TravellerEntity arriveAt(ServerLevel level, Travellers.Traveller traveller, ServerPlayer host,
                                           int index) {
        BlockPos rough = BlockPos.containing(host.position());
        if (!level.hasChunkAt(rough)) {
            return null;
        }
        TravellerEntity body = NpcEntities.TRAVELLER.get().create(level);
        if (body == null) {
            return null;
        }
        body.setup(traveller, Travellers.Place.FIELD, host.position(), 8.0);
        body.becomeCompanion(host, index);
        Vec3 at = standable(level, body.formationSpot(host)).or(() -> standable(level, host.position()))
                .orElse(host.position());
        body.moveTo(at.x, at.y, at.z, host.getYRot(), 0.0F);
        level.addFreshEntity(body);
        BODIES.put(traveller, body);
        TravellerMoves.trip(body, true);
        return body;
    }

    /** A traveller on its own round. */
    private static void round(Travellers.Traveller traveller, @Nullable TravellerEntity body, @Nullable ServerPlayer host,
                              ServerLevel realm, TravellerWorldState state, long now) {
        if (body != null && body.level() != realm) {
            body.discard();
            BODIES.remove(traveller);
            body = null;
        }
        Optional<Travellers.Plan> current = state.plan(traveller);
        if (current.isEmpty() || now >= current.get().until()) {
            if (body != null && current.isPresent() && (body.enemy() != null || body.isTalking()
                    || body.running() != null)) {
                // never in the middle of a fight or a talk: a little later
                state.set(traveller, new Travellers.Plan(current.get().place(), current.get().habitat(), now + 200));
            } else {
                state.set(traveller, nextPlan(traveller, current.orElse(null), host, realm, now));
                if (body != null) {
                    TravellerMoves.trip(body, false);
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
            return;
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

    private static void dismissAll(boolean visibly) {
        for (TravellerEntity body : BODIES.values()) {
            if (visibly) {
                TravellerMoves.trip(body, false);
            }
            body.discard();
        }
        BODIES.clear();
    }

    /** The next stop of a traveller's round (a habitat near the player most of the time). */
    private static Travellers.Plan nextPlan(Travellers.Traveller traveller, @Nullable Travellers.Plan finished,
                                           @Nullable ServerPlayer host, ServerLevel realm, long now) {
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
    @Nullable
    private static TravellerEntity appear(ServerLevel realm, Travellers.Traveller traveller, Travellers.Plan plan,
                                         Vec3 center, @Nullable ServerPlayer host) {
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
        Vec3 anchor = plan.place() == Travellers.Place.FIELD ? new Vec3(center.x, at.y, center.z) : at;
        body.moveTo(at.x, at.y, at.z, RANDOM.nextFloat() * 360.0F, 0.0F);
        body.setup(traveller, plan.place(), anchor, roam);
        realm.addFreshEntity(body);
        BODIES.put(traveller, body);
        if (host != null && host.distanceTo(body) < CALL) {
            TravellerMoves.trip(body, true);
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

    /**
     * A place to stand at or near {@code at} (the block itself, a block or two up or down, then the blocks around):
     * empty for feet and head, solid underfoot, no liquid. Empty when there is none or the ground is not loaded.
     */
    public static Optional<Vec3> standable(ServerLevel level, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        int[][] around = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}};
        for (int[] d : around) {
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                BlockPos feet = base.offset(d[0], dy, d[1]);
                if (level.hasChunkAt(feet) && fits(level, feet) && level.getFluidState(feet).isEmpty()) {
                    return Optional.of(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5));
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

    // ------------------------------------------------------------------------------------------- the party

    /** The travellers in the player's party (stored with the character). */
    public static Set<Travellers.Traveller> partyOf(ServerContext context, ServerPlayer player) {
        return Travellers.parse(context.playerData().extension(player.getUUID(), PARTY).orElse(""));
    }

    private static void saveParty(ServerContext context, ServerPlayer player, Set<Travellers.Traveller> party) {
        context.playerData().setExtension(player.getUUID(), PARTY, Travellers.format(party));
    }

    /** The bodies of the player's companions that are in the world now. */
    public static List<TravellerEntity> companionsOf(UUID player) {
        List<TravellerEntity> out = new ArrayList<>();
        for (TravellerEntity body : BODIES.values()) {
            if (body.isCompanion() && player.equals(body.ownerId()) && !body.isRemoved()) {
                out.add(body);
            }
        }
        out.sort((a, b) -> Integer.compare(a.formationSlot(), b.formationSlot()));
        return out;
    }

    /** {@code traveller} joins the player's party: the gesture, the light, the notice. */
    public static void recruit(ServerContext context, ServerPlayer player, TravellerEntity body) {
        Set<Travellers.Traveller> party = new LinkedHashSet<>(partyOf(context, player));
        if (party.contains(body.who())) {
            return;
        }
        party.add(body.who());
        saveParty(context, player, party);
        body.becomeCompanion(player, party.size() - 1);
        BODIES.put(body.who(), body);
        body.refreshMoves();
        body.cheer();
        player.sendSystemMessage(Component.translatable("message.eternalrealm.traveller.joined",
                Component.translatable(body.who().titleKey()).withStyle(ChatFormatting.GOLD),
                Component.translatable(body.who().nameKey()).withStyle(ChatFormatting.AQUA)));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.7F, 1.0F);
    }

    /** {@code traveller} leaves the party (talking to them, the party page): a farewell, and back to their round. */
    public static void dismiss(ServerContext context, ServerPlayer player, Travellers.Traveller traveller) {
        Set<Travellers.Traveller> party = new LinkedHashSet<>(partyOf(context, player));
        if (!party.remove(traveller)) {
            return;
        }
        saveParty(context, player, party);
        TravellerEntity body = BODIES.remove(traveller);
        if (body != null && !body.isRemoved()) {
            say(body, Travellers.lineKey(traveller, "farewell"));
            TravellerMoves.trip(body, false);
            body.discard();
        }
        AstoriaTravel.astoria(player.server).ifPresent(realm -> {
            TravellerWorldState state = TravellerWorldState.get(realm);
            state.set(traveller, nextPlan(traveller, null, player, realm, realm.getGameTime()));
        });
        player.sendSystemMessage(Component.translatable("message.eternalrealm.traveller.left",
                Component.translatable(traveller.nameKey()).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
    }

    /** Party frame rows of the player's companions (party HUD and page). */
    public static JsonArray hudRows(UUID player) {
        JsonArray rows = new JsonArray();
        for (TravellerEntity body : companionsOf(player)) {
            JsonObject row = new JsonObject();
            row.addProperty("id", body.getUUID().toString());
            row.addProperty("companion", body.who().id());
            row.addProperty("name", body.who().id());
            row.addProperty("nameKey", body.who().nameKey());
            row.addProperty("titleKey", body.who().titleKey());
            row.addProperty("leader", false);
            row.addProperty("online", true);
            row.addProperty("hp", Math.round(body.getHealth() * 10.0) / 10.0);
            row.addProperty("maxHp", Math.round(body.getMaxHealth() * 10.0) / 10.0);
            row.addProperty("down", body.isDown());
            row.addProperty("level", HUD_LEVEL);
            row.addProperty("job", body.who().job());
            rows.add(row);
        }
        return rows;
    }

    // ---------------------------------------------------------------------------------------- meeting, talk

    /** The player comes close: the first time, they meet. */
    private static void notice(ServerPlayer player, TravellerEntity body) {
        if (player.distanceTo(body) > MEET || !player.hasLineOfSight(body) || body.enemy() != null) {
            return;
        }
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || !context.playerData().isActive(player.getUUID())) {
            return;
        }
        Set<Travellers.Traveller> met = met(context, player);
        if (!met.contains(body.who())) {
            meet(context, player, body, met);
        }
    }

    private static Set<Travellers.Traveller> met(ServerContext context, ServerPlayer player) {
        return Travellers.parse(context.playerData().extension(player.getUUID(), MET).orElse(""));
    }

    private static void meet(ServerContext context, ServerPlayer player, TravellerEntity body,
                             Set<Travellers.Traveller> met) {
        met.add(body.who());
        context.playerData().setExtension(player.getUUID(), MET, Travellers.format(met));
        body.listenTo(player);
        List<DialogueLine> lines = new ArrayList<>();
        for (String key : Travellers.meetKeys(body.who())) {
            lines.add(new DialogueLine(body.who().nameKey(), key));
        }
        player.sendSystemMessage(Component.translatable("message.eternalrealm.traveller.met",
                Component.translatable(body.who().titleKey()).withStyle(ChatFormatting.GOLD),
                Component.translatable(body.who().nameKey()).withStyle(ChatFormatting.AQUA), met.size(),
                Travellers.Traveller.values().length).withStyle(ChatFormatting.GRAY));
        player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.6F, 1.2F);
        offer(context, player, body, lines);
    }

    /** The question: travel together? */
    private static void offer(ServerContext context, ServerPlayer player, TravellerEntity body,
                              List<DialogueLine> before) {
        List<DialogueLine> lines = new ArrayList<>(before);
        lines.add(new DialogueLine(body.who().nameKey(), Travellers.lineKey(body.who(), "ask")));
        context.game().story().ask(player, lines, List.of("traveller.eternalrealm.choice.invite",
                "traveller.eternalrealm.choice.later"), choice -> {
            if (choice == 0) {
                if (partyOf(context, player).size() >= Travellers.Traveller.values().length) {
                    return;
                }
                context.game().story().play(player, List.of(new DialogueLine(body.who().nameKey(),
                        Travellers.lineKey(body.who(), "accept"))), false, null);
                recruit(context, player, body);
            } else if (choice == 1) {
                context.game().story().play(player, List.of(new DialogueLine(body.who().nameKey(),
                        Travellers.lineKey(body.who(), "decline"))), false, null);
            }
        });
    }

    /** The player talks to a traveller (right click). */
    static void talk(ServerPlayer player, TravellerEntity body) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || body.isTalking() || body.running() != null || body.isDown()) {
            return;
        }
        Set<Travellers.Traveller> met = met(context, player);
        if (!met.contains(body.who())) {
            meet(context, player, body, met);
            return;
        }
        body.listenTo(player);
        player.playNotifySound(com.eternalrealm.sound.ERSounds.UI_NPC_TALK.get(), SoundSource.NEUTRAL, 0.7F,
                0.95F + body.getRandom().nextFloat() * 0.1F);
        if (body.isCompanion() && player.getUUID().equals(body.ownerId())) {
            String line = Travellers.partyKey(body.who(), body.getRandom().nextInt(Travellers.PARTY_LINES));
            context.game().story().ask(player, List.of(new DialogueLine(body.who().nameKey(), line)),
                    List.of("traveller.eternalrealm.choice.stay", "traveller.eternalrealm.choice.part"), choice -> {
                        if (choice == 1) {
                            dismiss(context, player, body.who());
                        }
                    });
            return;
        }
        int pick = body.getRandom().nextInt(Travellers.LINES_PER_PLACE);
        String line = Travellers.talkKey(body.who(), body.place(), pick);
        offer(context, player, body, List.of(new DialogueLine(body.who().nameKey(), line)));
    }

    /** Its call when it uses a skill or its ultimate (skills now and then, the ultimate always). */
    static void shout(TravellerEntity body, Travellers.Move move) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || !(body.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getServer().getTickCount();
        Long last = LAST_CALL.get(body.who());
        if (move.kind() != Travellers.Kind.ULTIMATE && last != null && now - last < CALL_GAP_TICKS) {
            return;
        }
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, body.getBoundingBox().inflate(CALL),
                player -> !player.isSpectator());
        if (near.isEmpty()) {
            return;
        }
        LAST_CALL.put(body.who(), now);
        context.game().story().bark(near, new DialogueLine(body.who().nameKey(), move.shoutKey()));
        if (move.kind() == Travellers.Kind.ULTIMATE) {
            for (ServerPlayer player : near) {
                player.displayClientMessage(Component.translatable(move.nameKey()).withStyle(ChatFormatting.GOLD,
                        ChatFormatting.BOLD), true);
            }
        }
    }

    /** A line of its own (falling, standing up, farewell) for the players around. */
    static void say(TravellerEntity body, String key) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || !(body.level() instanceof ServerLevel level)) {
            return;
        }
        List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, body.getBoundingBox().inflate(CALL),
                player -> !player.isSpectator());
        if (!near.isEmpty()) {
            context.game().story().bark(near, new DialogueLine(body.who().nameKey(), key));
        }
    }

    /** UI action of the party page: {@code traveller.dismiss {id: <traveller id>}} parts with a companion. */
    public static void registerActions() {
        com.eternalrealm.server.UiActions.register("traveller.dismiss", (player, context, args) -> {
            String id = com.eternalrealm.server.UiArgs.string(args, "id", 32);
            Travellers.Traveller.byId(id).ifPresent(traveller -> dismiss(context, player, traveller));
        });
    }

    // ------------------------------------------------------------------------------------------- testing

    /**
     * {@code /eternalrealm admin travellers summon [who]} brings them (met and in the party) to the player at once;
     * {@code part} sends them all back to their rounds. Single-player worlds only, like the travellers themselves.
     */
    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("eternalrealm")
                .then(net.minecraft.commands.Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(net.minecraft.commands.Commands.literal("travellers")
                                .then(net.minecraft.commands.Commands.literal("summon")
                                        .executes(ctx -> summonCommand(ctx.getSource(), null))
                                        .then(net.minecraft.commands.Commands.argument("who",
                                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .suggests((ctx, builder) -> net.minecraft.commands
                                                        .SharedSuggestionProvider.suggest(java.util.Arrays.stream(
                                                                Travellers.Traveller.values()).map(Travellers.Traveller::id),
                                                                builder))
                                                .executes(ctx -> summonCommand(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx,
                                                                "who")))))
                                .then(net.minecraft.commands.Commands.literal("part")
                                        .executes(ctx -> partCommand(ctx.getSource()))))));
    }

    private static int summonCommand(net.minecraft.commands.CommandSourceStack source, @Nullable String who)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || !allowed(source.getServer())) {
            source.sendFailure(Component.translatable("commands.eternalrealm.travellers.single_player"));
            return 0;
        }
        Travellers.Traveller only = null;
        if (who != null) {
            only = Travellers.Traveller.byId(who).orElse(null);
            if (only == null) {
                source.sendFailure(Component.translatable("commands.eternalrealm.travellers.unknown", who));
                return 0;
            }
        }
        int joined = summonAll(context, player, only);
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.travellers.summoned", joined), true);
        return joined;
    }

    private static int partCommand(net.minecraft.commands.CommandSourceStack source)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return 0;
        }
        int count = partyOf(context, player).size();
        partAll(context, player);
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.travellers.parted", count), true);
        return count;
    }

    /**
     * Admin: brings every traveller (or one) to the player at once, met and in the party (testing the companions
     * without searching the fields). @return how many joined
     */
    public static int summonAll(ServerContext context, ServerPlayer player, @Nullable Travellers.Traveller only) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        Set<Travellers.Traveller> met = met(context, player);
        int joined = 0;
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            if (only != null && traveller != only) {
                continue;
            }
            met.add(traveller);
            if (partyOf(context, player).contains(traveller)) {
                continue;
            }
            TravellerEntity body = BODIES.remove(traveller);
            if (body != null) {
                body.discard();
            }
            body = arriveAt(level, traveller, player, partyOf(context, player).size());
            if (body != null) {
                recruit(context, player, body);
                joined++;
            }
        }
        context.playerData().setExtension(player.getUUID(), MET, Travellers.format(met));
        return joined;
    }

    /** Admin: everyone parts with the player. */
    public static void partAll(ServerContext context, ServerPlayer player) {
        for (Travellers.Traveller traveller : partyOf(context, player)) {
            dismiss(context, player, traveller);
        }
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
