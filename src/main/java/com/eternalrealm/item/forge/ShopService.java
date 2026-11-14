package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.ItemPrices;
import com.eternalrealm.item.ShopCatalog;
import com.eternalrealm.item.bag.Bag;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * The item shop's counter for players (owner, 2026-11-14: 「アイテムショップのHUDをファンタジー風にオシャレにして」):
 * the server sends the wares and what the player could sell from the front and the back of the bag on channel
 * {@value #CHANNEL} (the client draws the shop, {@code ui.shop.ShopScreen}); a purchase ({@value #BUY}) or a sale
 * ({@value #SELL}) is checked here, answered on {@value #RESULT_CHANNEL} and followed by the shop again. The rules are
 * {@link ShopCatalog}'s; the test players still use the chest menu ({@link ShopMenu}) with the same prices.
 */
public final class ShopService {
    /** S2C: the shop ({@code {gold, level, wares: [...], sell: [...], refresh}}). */
    public static final String CHANNEL = "shop";
    /** S2C: the answer to a purchase or a sale ({@code {ok, kind, gold, message}}). */
    public static final String RESULT_CHANNEL = "shop_result";
    public static final String BUY = "shop.buy";
    public static final String SELL = "shop.sell";
    /** Most of one stack sold at once (a stack never holds more). */
    private static final int MAX_SELL = 64 * 64;

    private ShopService() {
    }

    /** What the counter answered: whether the deal was made, the gold it moved (+ received, − paid), the words. */
    public record Outcome(boolean ok, String kind, long gold, Component message) {
    }

    public static void register() {
        UiActions.register(BUY, (player, context, args) -> answer(player, buy(player, context, UiArgs.id(args, "id"),
                (int) UiArgs.number(args, "count", 1, ShopCatalog.MAX_PER_PURCHASE))));
        UiActions.register(SELL, (player, context, args) -> {
            int slot = (int) UiArgs.number(args, "slot", Bag.FIRST_SLOT, Bag.BACK_LAST);
            if (!Bag.inBag(slot)) {
                throw new UiArgs.InvalidArgumentException("not a bag slot: " + slot);
            }
            answer(player, sell(player, context, slot, UiArgs.id(args, "id"),
                    (int) UiArgs.number(args, "count", 1, MAX_SELL),
                    args.has("uuid") ? UiArgs.uuid(args, "uuid") : null));
        });
    }

    /** Tells the client how the deal went and shows it the shop as it is now. */
    private static void answer(ServerPlayer player, Outcome outcome) {
        JsonObject data = new JsonObject();
        data.addProperty("ok", outcome.ok());
        data.addProperty("kind", outcome.kind());
        data.addProperty("gold", outcome.gold());
        data.addProperty("message", Component.Serializer.toJson(outcome.message()));
        data.addProperty("t", System.nanoTime());
        UiData.send(player, RESULT_CHANNEL, data);
        send(player, true);
    }

    /** Opens the shop on the player's client. */
    public static void open(ServerPlayer player) {
        send(player, false);
    }

    private static void send(ServerPlayer player, boolean refresh) {
        UiData.send(player, CHANNEL, model(player, refresh));
    }

    /** The shop as the player sees it now. */
    public static JsonObject model(ServerPlayer player, boolean refresh) {
        JsonObject data = new JsonObject();
        Optional<CharacterSnapshot> snapshot = ServerContext.current()
                .flatMap(context -> context.playerData().snapshot(player.getUUID()));
        data.addProperty("gold", snapshot.map(CharacterSnapshot::gold).orElse(0L));
        data.addProperty("level", snapshot.map(CharacterSnapshot::level).orElse(1));
        data.addProperty("refresh", refresh);
        data.addProperty("t", System.nanoTime());
        Map<String, Integer> owned = Inventories.counts(player);
        JsonArray wares = new JsonArray();
        for (ShopCatalog.Ware ware : ShopCatalog.wares()) {
            if (Inventories.item(ware.itemId()) == null) {
                continue;
            }
            JsonObject row = new JsonObject();
            row.addProperty("id", ware.itemId());
            row.addProperty("section", ware.section().name());
            row.addProperty("price", ware.price());
            row.addProperty("level", ware.requiredLevel());
            row.addProperty("owned", owned.getOrDefault(ware.itemId(), 0));
            wares.add(row);
        }
        data.add("wares", wares);
        JsonArray sell = new JsonArray();
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            addSellable(sell, slot, player.getInventory().items.get(slot));
        }
        for (int index = 0; index < Bag.BACK_SIZE; index++) {
            addSellable(sell, Bag.BACK_FIRST + index, BagBack.get(player, index));
        }
        data.add("sell", sell);
        return data;
    }

    /** What the shop pays for one of the stack, and what kind of thing it is; empty if it buys none. */
    private record Offer(ShopCatalog.SellKind kind, long each) {
    }

    private static Optional<Offer> offer(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<EquipmentData> equipment = EquipmentStacks.read(stack);
        if (equipment.isPresent()) {
            return Optional.of(new Offer(ShopCatalog.SellKind.EQUIPMENT, ItemPrices.equipmentSellValue(equipment.get())));
        }
        if (stack.getItem() instanceof ConsumableItem consumable) {
            return Optional.of(new Offer(ShopCatalog.SellKind.CONSUMABLE,
                    ItemPrices.consumableSellPrice(consumable.consumable())));
        }
        OptionalLong each = ItemPrices.materialSellValue(Inventories.idOf(stack));
        return each.isPresent() ? Optional.of(new Offer(ShopCatalog.SellKind.MATERIAL, each.getAsLong()))
                : Optional.empty();
    }

    private static void addSellable(JsonArray out, int slot, ItemStack stack) {
        Optional<Offer> offer = offer(stack);
        if (offer.isEmpty() || offer.get().each() <= 0) {
            return;
        }
        JsonObject row = new JsonObject();
        row.addProperty("slot", slot);
        row.addProperty("id", Inventories.idOf(stack));
        row.addProperty("count", stack.getCount());
        row.addProperty("each", offer.get().each());
        row.addProperty("kind", offer.get().kind().id());
        EquipmentStacks.read(stack).ifPresent(data -> {
            if (data.uniqueId() != null) {
                row.addProperty("uuid", data.uniqueId().toString());
            }
            row.addProperty("rarity", data.rarity().rank());
        });
        out.add(row);
    }

    // ------------------------------------------------------------------ buying

    /** Buys {@code count} of the ware {@code itemId} for the player, if the counter agrees. */
    public static Outcome buy(ServerPlayer player, ServerContext context, String itemId, int count) {
        Optional<ShopCatalog.Ware> ware = ShopCatalog.ware(itemId);
        Item item = Inventories.item(itemId);
        long gold = context.playerData().snapshot(player.getUUID()).map(CharacterSnapshot::gold).orElse(0L);
        long room = 0;
        if (item != null) {
            ItemStack probe = new ItemStack(item);
            room = Bags.roomFor(player.getInventory(), probe) + (long) BagBack.roomFor(player, probe);
        }
        ShopCatalog.BuyCheck check = item == null ? ShopCatalog.BuyCheck.UNKNOWN
                : ShopCatalog.canBuy(itemId, count, gold, room);
        long total = ware.map(w -> ShopCatalog.total(w.price(), count)).orElse(0L);
        if (check == ShopCatalog.BuyCheck.OK && !context.playerData().spendGold(player.getUUID(), total)) {
            check = ShopCatalog.BuyCheck.NOT_ENOUGH_GOLD;
        }
        if (check != ShopCatalog.BuyCheck.OK) {
            return new Outcome(false, "buy", 0L, Component.translatable(check.translationKey(), total));
        }
        ItemStack shown = new ItemStack(item);
        int left = count;
        while (left > 0) {
            int amount = Math.min(left, shown.getMaxStackSize());
            Inventories.give(player, new ItemStack(item, amount));
            left -= amount;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6F, 1.3F);
        return new Outcome(true, "buy", -total, Component.translatable("message.eternalrealm.shop.bought",
                shown.getHoverName(), count, total));
    }

    // ------------------------------------------------------------------ selling

    /**
     * Sells {@code count} of what lies in bag slot {@code slot} (a front slot or a back one), if it is still the item
     * {@code itemId} (and, for a piece of equipment, the piece {@code uuid}: one at a time).
     */
    public static Outcome sell(ServerPlayer player, ServerContext context, int slot, String itemId, int count,
                               UUID uuid) {
        if (!Bag.inBag(slot)) {
            return new Outcome(false, "sell", 0L, Component.translatable("message.eternalrealm.shop.sell.moved"));
        }
        boolean back = Bag.isBackSlot(slot);
        ItemStack stack = back ? BagBack.get(player, Bag.backIndex(slot)) : player.getInventory().items.get(slot);
        Optional<Offer> offer = offer(stack);
        if (stack.isEmpty() || !Inventories.idOf(stack).equals(itemId) || offer.isEmpty()) {
            // the bag changed under the click, or the shop buys no such thing
            return new Outcome(false, "sell", 0L, Component.translatable(offer.isEmpty() && !stack.isEmpty()
                    ? "message.eternalrealm.shop.cannot_sell" : "message.eternalrealm.shop.sell.moved"));
        }
        if (offer.get().kind() == ShopCatalog.SellKind.EQUIPMENT) {
            UUID held = EquipmentStacks.read(stack).map(EquipmentData::uniqueId).orElse(null);
            if (uuid == null || !uuid.equals(held)) {
                return new Outcome(false, "sell", 0L, Component.translatable("message.eternalrealm.shop.sell.moved"));
            }
            count = 1;
        }
        int sold = Math.min(count, stack.getCount());
        long total = ShopCatalog.sellTotal(offer.get().each(), sold, stack.getCount());
        Component name = stack.getHoverName();
        boolean precious = EquipmentStacks.read(stack)
                .map(data -> data.rarity().rank() >= com.eternalrealm.item.ItemRarity.RARE.rank()).orElse(false);
        if (back) {
            ItemStack left = stack.copy();
            left.shrink(sold);
            BagBack.set(player, Bag.backIndex(slot), left);
        } else {
            stack.shrink(sold);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
        context.playerData().grantGold(player.getUUID(), total);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6F, 0.9F);
        return new Outcome(true, "sell", total, Component.translatable("message.eternalrealm.shop.sold", name, sold,
                total).withStyle(precious ? ChatFormatting.GOLD : ChatFormatting.GREEN));
    }
}
