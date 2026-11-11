package com.eternalrealm.estate.forge;

import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.RoomDesign;
import com.eternalrealm.estate.RoomRules;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The inn of the Starlight Manor (星灯りの館, docs/modules/estates.md): the innkeeper rents each player one room
 * ({@link RoomDesign}), moves them to another room with everything they stored (paying the difference, or getting half
 * of it back for a smaller one), takes a room back when it is empty (half its price back) and hands out what waits in
 * the keepsafe. Inside a room its door opens a little menu: back to where one came from, or to the capital. All
 * requests are checked here; the screens only show the documents ({@value #CHANNEL}, {@value #EXIT_CHANNEL}).
 */
public final class InnService {
    public static final String CHANNEL = "estate.inn";
    public static final String VIEW = "estate.inn.view";
    public static final String RENT = "estate.inn.rent";
    public static final String CHANGE = "estate.inn.change";
    public static final String GIVE_UP = "estate.inn.give_up";
    public static final String KEEPSAFE = "estate.inn.keepsafe";
    public static final String VISIT = "estate.inn.visit";
    public static final String EXIT_CHANNEL = "estate.exit";
    public static final String EXIT = "estate.exit";
    public static final String NOT_AT_INN = "message.eternalrealm.inn.not_at_inn";
    /** How close to the innkeeper a request must be made (blocks). */
    public static final double REACH = 7.0;
    private static final Pattern SLOT_NAME = Pattern.compile("er_slot:\"([a-z]+)\\.(\\d+)\"");

    private InnService() {
    }

    /** Mod construction: the screens' actions, the innkeeper, the room doors' exit menu, the ready notice. */
    static void register() {
        UiActions.register(VIEW, (player, context, args) -> send(player, false));
        UiActions.register(RENT, (player, context, args) -> {
            RoomDesign design = design(args);
            if (requireInn(player)) {
                rent(player, design);
            }
            send(player, false);
        });
        UiActions.register(CHANGE, (player, context, args) -> {
            RoomDesign design = design(args);
            if (requireInn(player)) {
                change(player, design);
            }
            send(player, false);
        });
        UiActions.register(GIVE_UP, (player, context, args) -> {
            if (requireInn(player)) {
                giveUp(player);
            }
            send(player, false);
        });
        UiActions.register(KEEPSAFE, (player, context, args) -> {
            if (requireInn(player)) {
                claimKeepsafe(player);
            }
            send(player, false);
        });
        UiActions.register(VISIT, (player, context, args) -> {
            if (requireInn(player)) {
                EstateService.estate(EstateKey.ROOM, player.getUUID()).ifPresent(key -> EstateService.enter(player, key));
            }
        });
        UiActions.register(EXIT, (player, context, args) -> {
            String to = UiArgs.id(args, "to");
            if (!EstateLevels.isEstateLevel(player.level())) {
                return;
            }
            if ("capital".equals(to)) {
                EstateService.toCapital(player);
            } else if ("back".equals(to)) {
                EstateService.leave(player);
            } else {
                throw new UiArgs.InvalidArgumentException("unknown exit " + to);
            }
        });
        Npcs.onInteract(NpcRole.INNKEEPER, (player, npc) -> open(player));
        RoomDoors.setExit((player, pos) -> openExit(player));
        EstateService.onReady(InnService::onReady);
        LedgerService.register();
        RecallService.register();
    }

    private static RoomDesign design(JsonObject args) {
        return RoomDesign.byId(UiArgs.id(args, "design"))
                .orElseThrow(() -> new UiArgs.InvalidArgumentException("unknown room design"));
    }

    // ------------------------------------------------------------------ the innkeeper

    /** Places the innkeeper at the capital's {@code npc.innkeeper} spot (the Starlight Manor's front desk). */
    public static void placeInnkeeper(ServerLevel astoria, HubLayout layout) {
        String spot = "npc." + NpcRole.INNKEEPER.id();
        layout.named(spot).ifPresent(pos -> Npcs.ensure(astoria, pos, NpcRole.INNKEEPER, layout.namedYaw(spot, 180.0F)));
    }

    /** Whether an innkeeper stands within {@value #REACH} blocks. */
    public static boolean atInn(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(REACH),
                mob -> Npcs.roleOf(mob).filter(role -> role == NpcRole.INNKEEPER).isPresent()).isEmpty();
    }

    private static boolean requireInn(ServerPlayer player) {
        if (atInn(player)) {
            return true;
        }
        player.displayClientMessage(Component.translatable(NOT_AT_INN).withStyle(ChatFormatting.RED), true);
        return false;
    }

    /** Talking to the innkeeper. */
    public static void open(ServerPlayer player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN,
                SoundSource.NEUTRAL, 0.6F, 1.1F);
        send(player, true);
    }

    static void send(ServerPlayer player, boolean open) {
        JsonObject data = document(player);
        data.addProperty("open", open);
        data.addProperty("nonce", System.nanoTime());
        UiData.send(player, CHANNEL, data);
    }

    /** The inn screen's document (public for the GameTests). */
    public static JsonObject document(ServerPlayer player) {
        JsonObject data = new JsonObject();
        Optional<EstateKey> key = EstateService.estate(EstateKey.ROOM, player.getUUID());
        Optional<RoomDesign> current = key.flatMap(k -> RoomDesign.byId(k.design()));
        EstateStatus status = key.map(EstateService::status).orElse(EstateStatus.NONE);
        Optional<EstateContents.Found> contents = key.flatMap(EstateService::contents);
        data.addProperty("atInn", atInn(player));
        data.addProperty("available", EstateService.available());
        data.addProperty("gold", gold(player));
        data.addProperty("room", current.map(RoomDesign::id).orElse(""));
        data.addProperty("phase", status.phase().name().toLowerCase(java.util.Locale.ROOT));
        data.addProperty("percent", status.percent());
        data.addProperty("stored", contents.map(EstateContents.Found::stacks).orElse(0));
        data.addProperty("empty", contents.map(InnService::isEmpty).orElse(true));
        data.addProperty("refund", current.map(RoomRules::refund).orElse(0L));
        data.addProperty("keepsafe", EstateService.keepsafe(player.getUUID()).size());
        Map<String, Integer> stored = contents.map(InnService::storedByName).orElse(Map.of());
        JsonArray designs = new JsonArray();
        for (RoomDesign design : RoomDesign.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", design.id());
            json.addProperty("price", design.price());
            current.ifPresent(from -> json.addProperty("cost", RoomRules.changeCost(from, design)));
            Set<String> names = storageNames(player.server, design);
            JsonObject storage = new JsonObject();
            for (Map.Entry<String, Integer> kind : countByKind(names).entrySet()) {
                storage.addProperty(kind.getKey(), kind.getValue());
            }
            json.add("storage", storage);
            // moving there: pieces of the same name keep their slots; what lies in pieces it lacks (and on the
            // floor) goes into its free space, and what does not fit waits in the keepsafe (an estimate: shelves
            // only take books)
            int rehomed = 0;
            int occupied = 0;
            for (Map.Entry<String, Integer> piece : stored.entrySet()) {
                if (names.contains(piece.getKey())) {
                    occupied += piece.getValue();
                } else {
                    rehomed += piece.getValue();
                }
            }
            int capacity = capacity(names);
            json.addProperty("rehome", rehomed);
            json.addProperty("keepsafe", Math.max(0, rehomed - Math.max(0, capacity - occupied)));
            designs.add(json);
        }
        data.add("designs", designs);
        return data;
    }

    private static boolean isEmpty(EstateContents.Found found) {
        return found.isEmpty() && found.loose.isEmpty();
    }

    /** Stacks per named storage piece (unnamed pieces and the floor count as "" ). */
    private static Map<String, Integer> storedByName(EstateContents.Found found) {
        Map<String, Integer> stored = new HashMap<>();
        for (EstateContents.Holder holder : found.holders()) {
            int stacks = holder.stacks();
            if (stacks > 0) {
                stored.merge(holder.name() == null ? "" : holder.name(), stacks, Integer::sum);
            }
        }
        if (!found.loose.isEmpty()) {
            stored.merge("", found.loose.size(), Integer::sum);
        }
        return stored;
    }

    /** The storage piece names of a room design (from its blueprint). */
    static Set<String> storageNames(MinecraftServer server, RoomDesign design) {
        Set<String> names = new LinkedHashSet<>();
        Optional<Blueprint> blueprint = EstateBlueprints.get(server, EstateKey.ROOM, design.id());
        if (blueprint.isEmpty()) {
            return names;
        }
        for (Blueprint.BlockEntityData blockEntity : blueprint.get().blockEntities()) {
            collect(blockEntity.snbt(), names);
        }
        for (Blueprint.EntityData entity : blueprint.get().entities()) {
            collect(entity.snbt(), names);
        }
        return names;
    }

    private static void collect(String snbt, Set<String> names) {
        if (snbt == null) {
            return;
        }
        Matcher matcher = SLOT_NAME.matcher(snbt);
        while (matcher.find()) {
            names.add(matcher.group(1) + "." + matcher.group(2));
        }
    }

    /** Container slots of a design's storage (chests and barrels 27, shelves 6; stands and frames hold no stacks). */
    private static int capacity(Set<String> names) {
        int slots = 0;
        for (String name : names) {
            if (name.startsWith("chest.") || name.startsWith("barrel.")) {
                slots += 27;
            } else if (name.startsWith("shelf.")) {
                slots += 6;
            }
        }
        return slots;
    }

    private static Map<String, Integer> countByKind(Set<String> names) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String name : names) {
            counts.merge(name.substring(0, name.indexOf('.')), 1, Integer::sum);
        }
        return counts;
    }

    private static long gold(ServerPlayer player) {
        return ServerContext.current().map(context -> context.playerData().gold(player.getUUID())).orElse(0L);
    }

    private static boolean spend(ServerPlayer player, long amount) {
        return amount <= 0 || ServerContext.current()
                .map(context -> context.playerData().spendGold(player.getUUID(), amount)).orElse(false);
    }

    private static void grant(ServerPlayer player, long amount) {
        if (amount > 0) {
            ServerContext.current().ifPresent(context -> context.playerData().grantGold(player.getUUID(), amount));
        }
    }

    private static RoomRules.Outcome tell(ServerPlayer player, RoomRules.Outcome outcome, Object... args) {
        player.displayClientMessage(Component.translatable(outcome.messageKey(), args)
                .withStyle(outcome.ok() ? ChatFormatting.GREEN : ChatFormatting.RED), !outcome.ok());
        return outcome;
    }

    // ------------------------------------------------------------------ renting

    /** Rents {@code design} for its price; the room is built at once (a few seconds). */
    public static RoomRules.Outcome rent(ServerPlayer player, RoomDesign design) {
        Optional<EstateKey> key = EstateService.estate(EstateKey.ROOM, player.getUUID());
        RoomRules.Outcome outcome = RoomRules.rent(key.flatMap(k -> RoomDesign.byId(k.design())), design, gold(player));
        if (key.isPresent() && RoomDesign.byId(key.get().design()).isEmpty()) {
            outcome = RoomRules.Outcome.HAS_ROOM;
        }
        if (!outcome.ok()) {
            return tell(player, outcome, design.price());
        }
        if (!spend(player, design.price())) {
            return tell(player, RoomRules.Outcome.NOT_ENOUGH_GOLD, design.price());
        }
        EstateResult claimed = EstateService.claim(design.key(player.getUUID()));
        if (!claimed.ok()) {
            grant(player, design.price());
            player.displayClientMessage(Component.translatable(claimed.messageKey(), 0).withStyle(ChatFormatting.RED),
                    true);
            return RoomRules.Outcome.BUSY;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LODESTONE_COMPASS_LOCK,
                SoundSource.PLAYERS, 1.0F, 1.2F);
        return tell(player, RoomRules.Outcome.OK, Component.translatable(design.translationKey()), design.price());
    }

    /** Moves to {@code design} with everything stored (pays the difference, or gets half of it back). */
    public static RoomRules.Outcome change(ServerPlayer player, RoomDesign design) {
        Optional<EstateKey> key = EstateService.estate(EstateKey.ROOM, player.getUUID());
        Optional<RoomDesign> current = key.flatMap(k -> RoomDesign.byId(k.design()));
        boolean busy = key.map(k -> EstateService.status(k).phase() != EstateStatus.Phase.READY).orElse(false);
        RoomRules.Outcome outcome = RoomRules.change(current, design, gold(player), busy);
        if (!outcome.ok()) {
            return tell(player, outcome, current.map(from -> RoomRules.changeCost(from, design)).orElse(0L));
        }
        long cost = RoomRules.changeCost(current.get(), design);
        if (!spend(player, cost)) {
            return tell(player, RoomRules.Outcome.NOT_ENOUGH_GOLD, cost);
        }
        EstateResult moved = EstateService.redesign(key.get(), design.id());
        if (!moved.ok()) {
            grant(player, Math.max(0L, cost));
            player.displayClientMessage(Component.translatable(moved.messageKey(), 0).withStyle(ChatFormatting.RED),
                    true);
            return RoomRules.Outcome.BUSY;
        }
        grant(player, -cost);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LODESTONE_COMPASS_LOCK,
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.eternalrealm.inn.changed",
                Component.translatable(design.translationKey())).withStyle(ChatFormatting.GREEN), false);
        return RoomRules.Outcome.OK;
    }

    /** Gives the room back (only when nothing is stored in it): half its price is handed back. */
    public static RoomRules.Outcome giveUp(ServerPlayer player) {
        Optional<EstateKey> key = EstateService.estate(EstateKey.ROOM, player.getUUID());
        Optional<RoomDesign> current = key.flatMap(k -> RoomDesign.byId(k.design()));
        boolean busy = key.map(k -> EstateService.status(k).phase() != EstateStatus.Phase.READY).orElse(false);
        boolean empty = key.flatMap(EstateService::contents).map(InnService::isEmpty).orElse(true);
        RoomRules.Outcome outcome = key.isPresent() && current.isEmpty() ? RoomRules.Outcome.OK
                : RoomRules.giveUp(current, busy, empty);
        if (!outcome.ok()) {
            return tell(player, outcome);
        }
        if (!EstateService.release(key.get()).ok()) {
            return tell(player, RoomRules.Outcome.BUSY);
        }
        long refund = current.map(RoomRules::refund).orElse(0L);
        grant(player, refund);
        player.displayClientMessage(Component.translatable("message.eternalrealm.inn.given_up", refund)
                .withStyle(ChatFormatting.YELLOW), false);
        return RoomRules.Outcome.OK;
    }

    /** Hands over what waits in the keepsafe (as far as the bag has room). */
    public static int claimKeepsafe(ServerPlayer player) {
        int given = EstateService.claimKeepsafe(player, player.getUUID());
        int left = EstateService.keepsafe(player.getUUID()).size();
        player.displayClientMessage(Component.translatable(left > 0 ? "message.eternalrealm.inn.keepsafe_partly"
                : "message.eternalrealm.inn.keepsafe", given, left).withStyle(given > 0 ? ChatFormatting.GREEN
                : ChatFormatting.GRAY), true);
        return given;
    }

    private static void onReady(ServerLevel level, EstateKey key) {
        if (!EstateKey.ROOM.equals(key.kind())) {
            return;
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(key.owner());
        if (owner != null) {
            Component name = RoomDesign.byId(key.design()).map(design -> (Component) Component.translatable(
                    design.translationKey())).orElse(Component.literal(key.design()));
            owner.displayClientMessage(Component.translatable("message.eternalrealm.inn.ready", name)
                    .withStyle(ChatFormatting.GOLD), false);
            owner.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, 1.2F);
            if (owner.containerMenu == owner.inventoryMenu) {
                send(owner, false);
            }
        }
    }

    // ------------------------------------------------------------------ leaving a room

    /** The room's door from inside: a little menu (back to where one came from, the capital, or stay). */
    static void openExit(ServerPlayer player) {
        JsonObject data = new JsonObject();
        data.addProperty("open", true);
        data.addProperty("back", EstateReturns.get(player).filter(point -> EstateReturns.usable(player, point))
                .map(point -> point.dimension().location().toString()).orElse(""));
        data.addProperty("nonce", System.nanoTime());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOODEN_DOOR_OPEN,
                SoundSource.BLOCKS, 0.5F, 1.1F);
        UiData.send(player, EXIT_CHANNEL, data);
    }

    /** Where the capital's lobby is (for the GameTests: the fallback when there is no way back). */
    public static Optional<BlockPos> lobby(MinecraftServer server) {
        return com.eternalrealm.world.forge.AstoriaTravel.astoria(server).map(astoria -> {
            HubLayout layout = Capital.layout(astoria);
            return layout.named(EstateService.LOBBY_SPOT).orElse(layout.spawnPoint());
        });
    }

    /** For tests: whether the player has a room of their own. */
    public static Optional<RoomDesign> roomOf(UUID player) {
        return EstateService.estate(EstateKey.ROOM, player).flatMap(key -> RoomDesign.byId(key.design()));
    }
}
