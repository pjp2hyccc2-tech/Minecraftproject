package com.eternalrealm.mount.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.mount.MountIndividual;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountLooks;
import com.eternalrealm.mount.MountMotion;
import com.eternalrealm.mount.MountOffers;
import com.eternalrealm.mount.MountOwnership;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.MountTraits;
import com.eternalrealm.npc.forge.NpcRole;
import com.eternalrealm.npc.forge.Npcs;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStoppingEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * The stable (docs/modules/mounts.md): the stablemaster's screen and the choose key's. At the stablemaster a player
 * sees {@value MountRules#OFFERS} horses and griffins with their looks, never their values, and buys one (its values
 * are revealed then), pays {@value MountRules#REROLL_PERCENT} % of the price to be shown others, makes kept mounts
 * into deeds and takes deeds in. Anywhere, the choose key lists the kept mounts with their values to pick and call
 * one. The offers stay the same until one is bought or others are asked for (logging out does not change them).
 */
public final class MountStable {
    /** What the stablemaster shows each player, by kind. */
    private static final Map<UUID, Map<MountKind, List<MountIndividual>>> OFFERS = new HashMap<>();
    private static final SplittableRandom RANDOM = new SplittableRandom();

    private MountStable() {
    }

    static void register() {
        UiActions.register(MountRules.BUY_ACTION, (player, context, args) -> {
            MountKind kind = kind(args);
            int index = (int) UiArgs.number(args, "index", 0, MountRules.OFFERS - 1);
            if (requireStable(player)) {
                MountIndividual chosen = offer(player, kind, index);
                if (buyChosen(player, chosen) == MountRules.Purchase.OK) {
                    send(player, false, true, chosen);
                    return;
                }
            }
            send(player, false, true, null);
        });
        UiActions.register(MountRules.REROLL_ACTION, (player, context, args) -> {
            MountKind kind = kind(args);
            if (requireStable(player)) {
                reroll(player, kind);
            }
            send(player, false, true, null);
        });
        UiActions.register(MountRules.SELECT_ACTION, (player, context, args) -> {
            UUID id = UiArgs.uuid(args, "id");
            boolean call = args.has("call") && UiArgs.flag(args, "call");
            if (call) {
                choose(player, id);
            } else {
                MountService.select(player, id);
                send(player, false, atStable(player), null);
            }
        });
        UiActions.register(MountRules.DEED_ACTION, (player, context, args) -> {
            UUID id = UiArgs.uuid(args, "id");
            if (requireStable(player)) {
                makeDeed(player, id);
            }
            send(player, false, true, null);
        });
        UiActions.register(MountRules.REDEEM_ACTION, (player, context, args) -> {
            int slot = (int) UiArgs.number(args, "slot", Bag.FIRST_SLOT, Bag.LAST_SLOT);
            if (requireStable(player)) {
                redeem(player, slot);
            }
            send(player, false, true, null);
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(MountStable::onServerStopping);
    }

    private static MountKind kind(JsonObject args) {
        return MountKind.byId(UiArgs.string(args, "kind", 16))
                .orElseThrow(() -> new UiArgs.InvalidArgumentException("unknown mount kind"));
    }

    // ------------------------------------------------------------------ opening

    /** The stablemaster: buying, deeds and the kept mounts. */
    public static void open(ServerPlayer player) {
        send(player, true, true, null);
    }

    /** The choose key: the kept mounts, to pick and call one. */
    public static void openChooser(ServerPlayer player) {
        send(player, true, false, null);
    }

    /** Whether a stablemaster stands within {@value MountRules#STABLE_REACH} blocks. */
    public static boolean atStable(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(MountRules.STABLE_REACH),
                mob -> Npcs.roleOf(mob).filter(role -> role == NpcRole.STABLE_MASTER).isPresent()).isEmpty();
    }

    private static boolean requireStable(ServerPlayer player) {
        if (atStable(player)) {
            return true;
        }
        player.displayClientMessage(Component.translatable(MountRules.NOT_AT_STABLE).withStyle(ChatFormatting.RED),
                true);
        return false;
    }

    // ------------------------------------------------------------------ buying

    /** What the stablemaster shows {@code player} of {@code kind} (rolled the first time). */
    public static List<MountIndividual> offers(ServerPlayer player, MountKind kind) {
        return OFFERS.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(MountKind.class))
                .computeIfAbsent(kind, k -> MountOffers.roll(k, RANDOM));
    }

    private static MountIndividual offer(ServerPlayer player, MountKind kind, int index) {
        if (!MountLooks.varies(kind)) {
            return MountIndividual.standard(UUID.randomUUID(), kind);
        }
        List<MountIndividual> shown = offers(player, kind);
        return shown.get(Math.max(0, Math.min(shown.size() - 1, index)));
    }

    /** Buys offer {@code index} of {@code kind}; on success the stablemaster brings new ones of that kind. */
    public static MountRules.Purchase buyOffer(ServerPlayer player, MountKind kind, int index) {
        return buyChosen(player, offer(player, kind, index));
    }

    private static MountRules.Purchase buyChosen(ServerPlayer player, MountIndividual chosen) {
        MountKind kind = chosen.kind();
        MountRules.Purchase result = MountService.buy(player, chosen);
        if (result == MountRules.Purchase.OK && MountLooks.varies(kind)) {
            OFFERS.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(MountKind.class))
                    .put(kind, MountOffers.roll(kind, RANDOM));
            MountTraits.Grade grade = chosen.grade();
            player.displayClientMessage(Component.translatable(MountRules.REVEAL, chosen.traits().total(),
                    Component.translatable(grade.translationKey())).withStyle(style -> style.withColor(
                    net.minecraft.network.chat.TextColor.fromRgb(grade.color()))), false);
            if (grade == MountTraits.Grade.SS) {
                Component news = Component.translatable(MountRules.LUCKY, player.getDisplayName(),
                        Component.translatable(grade.translationKey()), Component.translatable(kind.translationKey()))
                        .withStyle(ChatFormatting.GOLD);
                for (ServerPlayer each : player.server.getPlayerList().getPlayers()) {
                    each.sendSystemMessage(news);
                }
            }
        }
        return result;
    }

    /** New offers of {@code kind} for {@link MountRules#rerollCost} gold. */
    public static boolean reroll(ServerPlayer player, MountKind kind) {
        if (!MountLooks.varies(kind)) {
            return false;
        }
        long cost = MountRules.rerollCost(kind);
        boolean paid = ServerContext.current().map(context -> context.playerData().spendGold(player.getUUID(), cost))
                .orElse(false);
        if (!paid) {
            player.displayClientMessage(Component.translatable(MountRules.NOT_ENOUGH_GOLD, cost)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        OFFERS.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(MountKind.class))
                .put(kind, MountOffers.roll(kind, RANDOM));
        player.displayClientMessage(Component.translatable(MountRules.REROLLED,
                Component.translatable(kind.translationKey())).withStyle(ChatFormatting.YELLOW), true);
        player.playNotifySound(SoundEvents.HORSE_SADDLE, SoundSource.PLAYERS, 0.6F, 1.0F);
        return true;
    }

    // ------------------------------------------------------------------ choosing

    /** Picks kept mount {@code id} and calls it (getting off the current mount first if it is another one). */
    static void choose(ServerPlayer player, UUID id) {
        if (!MountService.select(player, id)) {
            return;
        }
        if (player.getVehicle() instanceof MountEntity mount) {
            if (mount.individualId().filter(id::equals).isPresent()) {
                return;
            }
            MountService.dismiss(player, false);
        }
        if (!MountService.isCalling(player)) {
            MountService.startCall(player);
        }
    }

    // ------------------------------------------------------------------ deeds

    /** Makes kept mount {@code id} into a deed in the bag. */
    public static boolean makeDeed(ServerPlayer player, UUID id) {
        MountOwnership owned = MountService.ownership(player);
        Optional<MountIndividual> mount = owned.mount(id);
        if (mount.isEmpty()) {
            return false;
        }
        if (!MountRules.tradeable(mount.get().kind())) {
            player.displayClientMessage(Component.translatable(MountRules.DEED_NOT_TRADEABLE)
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (freeBagSlot(player) < 0) {
            player.displayClientMessage(Component.translatable(MountRules.DEED_BAG_FULL).withStyle(ChatFormatting.RED),
                    true);
            return false;
        }
        if (player.getVehicle() instanceof MountEntity ridden && ridden.individualId().filter(id::equals).isPresent()) {
            MountService.dismiss(player, false);
        }
        if (!MountService.save(player, owned.without(id))) {
            return false;
        }
        ItemStack deed = MountDeeds.create(mount.get());
        player.getInventory().items.set(freeBagSlot(player), deed);
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable(MountRules.DEED_MADE, deed.getHoverName())
                .withStyle(ChatFormatting.GOLD), false);
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.0F);
        MountService.sendState(player);
        return true;
    }

    /** Takes the deed in bag slot {@code slot} into the stable. */
    public static MountRules.Redeem redeem(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().items.get(slot);
        Optional<MountIndividual> mount = MountDeeds.read(stack);
        if (mount.isEmpty()) {
            player.displayClientMessage(Component.translatable(MountRules.DEED_BROKEN).withStyle(ChatFormatting.RED),
                    true);
            return MountRules.Redeem.NOT_TRADEABLE;
        }
        MountOwnership owned = MountService.ownership(player);
        MountRules.Redeem result = MountRules.redeem(mount.get(), level(player), owned);
        switch (result) {
            case OK -> {
                player.getInventory().items.set(slot, ItemStack.EMPTY);
                player.getInventory().setChanged();
                if (!MountService.keep(player, mount.get())) {
                    player.getInventory().items.set(slot, stack); // could not record it: the deed stays
                    return MountRules.Redeem.STABLE_FULL;
                }
                player.displayClientMessage(Component.translatable(MountRules.DEED_TAKEN,
                        Component.translatable(mount.get().kind().translationKey()).withStyle(ChatFormatting.GOLD))
                        .withStyle(ChatFormatting.GREEN), false);
                player.playNotifySound(SoundEvents.HORSE_ARMOR, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            case LEVEL_TOO_LOW -> player.displayClientMessage(Component.translatable(MountRules.LEVEL_TOO_LOW,
                    mount.get().kind().requiredLevel()).withStyle(ChatFormatting.RED), true);
            case STABLE_FULL -> player.displayClientMessage(Component.translatable(MountRules.STABLE_FULL,
                    MountRules.STABLE_CAPACITY).withStyle(ChatFormatting.RED), true);
            case ALREADY_KEPT -> player.displayClientMessage(Component.translatable(MountRules.DEED_ALREADY_KEPT)
                    .withStyle(ChatFormatting.RED), true);
            case NOT_TRADEABLE -> player.displayClientMessage(Component.translatable(MountRules.DEED_NOT_TRADEABLE)
                    .withStyle(ChatFormatting.RED), true);
        }
        return result;
    }

    private static int freeBagSlot(ServerPlayer player) {
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            if (player.getInventory().items.get(slot).isEmpty()) {
                return slot;
            }
        }
        return -1;
    }

    private static int level(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::level).orElse(0);
    }

    // ------------------------------------------------------------------ the screen

    /**
     * The stable screen ({@value MountRules#STABLE_CHANNEL}). The offers carry their looks only: their values never
     * leave the server before they are bought. {@code reveal} is a mount just bought, shown with its values.
     */
    static void send(ServerPlayer player, boolean open, boolean stable, MountIndividual reveal) {
        JsonObject data = screen(player, stable, reveal);
        data.addProperty("open", open);
        UiData.send(player, MountRules.STABLE_CHANNEL, data);
    }

    /** The stable screen's document (without {@code open}); public for the GameTests. */
    public static JsonObject screen(ServerPlayer player, boolean stable, MountIndividual reveal) {
        MountOwnership owned = MountService.ownership(player);
        int level = level(player);
        long gold = ServerContext.current().map(context -> context.playerData().gold(player.getUUID())).orElse(0L);
        boolean here = stable && atStable(player);
        JsonObject data = new JsonObject();
        data.addProperty("mode", stable ? "stable" : "choose");
        data.addProperty("atStable", here);
        data.addProperty("gold", gold);
        data.addProperty("level", level);
        data.addProperty("capacity", MountRules.STABLE_CAPACITY);
        if (stable) {
            JsonArray kinds = new JsonArray();
            for (MountKind kind : MountKind.values()) {
                JsonObject json = new JsonObject();
                json.addProperty("kind", kind.id());
                json.addProperty("level", kind.requiredLevel());
                json.addProperty("price", kind.price());
                json.addProperty("flies", kind.flies());
                json.addProperty("speed", String.format(Locale.ROOT, "%.1f",
                        kind.stats().topSpeed() / MountMotion.SPRINT_SPEED));
                json.addProperty("varies", MountLooks.varies(kind));
                json.addProperty("reroll", MountRules.rerollCost(kind));
                json.addProperty("status", MountRules.purchase(kind, level, gold, owned).name()
                        .toLowerCase(Locale.ROOT));
                if (MountLooks.varies(kind)) {
                    JsonArray offers = new JsonArray();
                    List<MountIndividual> shown = offers(player, kind);
                    for (int i = 0; i < shown.size(); i++) {
                        JsonObject offer = new JsonObject();
                        offer.addProperty("index", i);
                        offer.addProperty("coat", shown.get(i).look().coat());
                        offer.addProperty("shape", shown.get(i).look().shape());
                        offer.addProperty("rare", MountLooks.rare(kind, shown.get(i).look()));
                        offers.add(offer);
                    }
                    json.add("offers", offers);
                }
                kinds.add(json);
            }
            data.add("kinds", kinds);
            JsonArray deeds = new JsonArray();
            for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
                int bagSlot = slot;
                MountDeeds.read(player.getInventory().items.get(slot)).ifPresent(mount -> {
                    JsonObject json = mountJson(mount, owned, player);
                    json.addProperty("slot", bagSlot);
                    json.addProperty("redeem", MountRules.redeem(mount, level, owned).name().toLowerCase(Locale.ROOT));
                    deeds.add(json);
                });
            }
            data.add("deeds", deeds);
        }
        JsonArray mounts = new JsonArray();
        for (MountIndividual mount : owned.mounts()) {
            mounts.add(mountJson(mount, owned, player));
        }
        data.add("mounts", mounts);
        if (reveal != null) {
            data.add("reveal", mountJson(reveal, owned, player));
        }
        return data;
    }

    private static JsonObject mountJson(MountIndividual mount, MountOwnership owned, ServerPlayer player) {
        JsonObject json = new JsonObject();
        json.addProperty("id", mount.id().toString());
        json.addProperty("kind", mount.kind().id());
        json.addProperty("coat", mount.look().coat());
        json.addProperty("shape", mount.look().shape());
        json.addProperty("rare", MountLooks.rare(mount.kind(), mount.look()));
        json.addProperty("varies", MountLooks.varies(mount.kind()));
        json.addProperty("speed", mount.traits().speed());
        json.addProperty("accel", mount.traits().acceleration());
        json.addProperty("lift", mount.traits().lift());
        json.addProperty("total", mount.traits().total());
        json.addProperty("grade", mount.grade().id());
        json.addProperty("selected", owned.selectedOrFirst().filter(selected -> selected.id().equals(mount.id()))
                .isPresent());
        json.addProperty("riding", player.getVehicle() instanceof MountEntity ridden
                && ridden.individualId().filter(mount.id()::equals).isPresent());
        json.addProperty("tradeable", MountRules.tradeable(mount.kind()));
        return json;
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        OFFERS.clear();
    }
}
