package com.eternalrealm.item.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.ItemPrices;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.Materials;
import com.eternalrealm.menu.MenuItems;
import com.eternalrealm.menu.ServerMenu;
import com.eternalrealm.server.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The item shop: buys consumables, enhancement stones and reroll crystals (click: 1, shift-click: 10) and buys back
 * what the player clicks in their own inventory (materials and consumables as a whole stack; equipment only with a
 * shift-click so a good piece is not sold by accident).
 */
public final class ShopMenu {
    private static final int ROWS = 4;
    /** The menu slot of the first consumable on the shelf; the others follow in their order. */
    private static final int FIRST_SHELF = 10;

    private ShopMenu() {
    }

    /** The menu slot where {@code consumable} is sold (a click there buys one, a shift-click ten). */
    public static int shelfSlot(Consumable consumable) {
        return FIRST_SHELF + consumable.ordinal();
    }

    public static void open(ServerPlayer player) {
        ServerMenu.open(player, Component.translatable("screen.eternalrealm.shop.title"), ROWS, ShopMenu::fill);
    }

    private static void fill(ServerMenu menu) {
        ServerPlayer player = menu.viewer();
        for (int slot = 0; slot < menu.size(); slot++) {
            menu.set(slot, MenuItems.filler(), null);
        }
        for (Consumable consumable : Consumable.values()) {
            Item item = ERGearItems.consumable(consumable);
            long price = ItemPrices.consumableShopPrice(consumable);
            menu.set(shelfSlot(consumable), priced(new ItemStack(item), price, consumable.requiredLevel()),
                    (p, button, shift) -> {
                        buy(p, new ItemStack(item), price, shift ? 10 : 1);
                        menu.refresh();
                    });
        }
        int slot = 19;
        for (String id : List.of(Materials.ENHANCEMENT_STONE, Materials.REROLL_CRYSTAL)) {
            OptionalLong price = ItemPrices.materialShopPrice(id);
            Item item = ERGearItems.material(id);
            if (price.isPresent() && item != null) {
                long each = price.getAsLong();
                menu.set(slot++, priced(new ItemStack(item), each, 1), (p, button, shift) -> {
                    buy(p, new ItemStack(item), each, shift ? 10 : 1);
                    menu.refresh();
                });
            }
        }
        long gold = ServerContext.current().flatMap(context -> context.playerData().snapshot(player.getUUID()))
                .map(CharacterSnapshot::gold).orElse(0L);
        menu.set(31, MenuItems.icon(Items.GOLD_INGOT, Component.translatable("screen.eternalrealm.shop.gold", gold)
                .withStyle(ChatFormatting.GOLD), List.of(Component.translatable("screen.eternalrealm.shop.sell_hint"))),
                null);
        menu.onInventoryClick((p, inventorySlot, stack, shift) -> {
            sell(p, inventorySlot, stack, shift);
            menu.refresh();
        });
    }

    private static ItemStack priced(ItemStack base, long price, int requiredLevel) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.translatable("screen.eternalrealm.shop.price", price).withStyle(ChatFormatting.GOLD));
        if (requiredLevel > 1) {
            lore.add(Component.translatable("tooltip.eternalrealm.equipment.required_level", requiredLevel));
        }
        lore.add(Component.translatable("screen.eternalrealm.shop.buy_hint"));
        return MenuItems.icon(base, base.getHoverName(), lore, false);
    }

    private static void buy(ServerPlayer player, ItemStack item, long price, int count) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null) {
            return;
        }
        long total = price * count;
        if (!context.playerData().spendGold(player.getUUID(), total)) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.shop.not_enough_gold", total)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int left = count;
        while (left > 0) {
            int amount = Math.min(left, item.getMaxStackSize());
            Inventories.give(player, item.copyWithCount(amount));
            left -= amount;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6F, 1.3F);
        player.displayClientMessage(Component.translatable("message.eternalrealm.shop.bought", item.getHoverName(), count,
                total).withStyle(ChatFormatting.GREEN), true);
    }

    private static void sell(ServerPlayer player, int inventorySlot, ItemStack stack, boolean shift) {
        ServerContext context = ServerContext.current().orElse(null);
        if (context == null || stack.isEmpty()) {
            return;
        }
        long value;
        int count;
        Optional<EquipmentData> equipment = EquipmentStacks.read(stack);
        if (equipment.isPresent()) {
            if (!shift) {
                player.displayClientMessage(Component.translatable("message.eternalrealm.shop.sell_equipment_shift")
                        .withStyle(ChatFormatting.YELLOW), true);
                return;
            }
            value = ItemPrices.equipmentSellValue(equipment.get());
            count = 1;
        } else if (stack.getItem() instanceof ConsumableItem consumable) {
            value = ItemPrices.consumableSellPrice(consumable.consumable());
            count = stack.getCount();
        } else {
            OptionalLong each = ItemPrices.materialSellValue(Inventories.idOf(stack));
            if (each.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.eternalrealm.shop.cannot_sell")
                        .withStyle(ChatFormatting.GRAY), true);
                return;
            }
            value = each.getAsLong();
            count = stack.getCount();
        }
        ItemStack inSlot = player.getInventory().getItem(inventorySlot);
        if (inSlot != stack && !ItemStack.matches(inSlot, stack)) {
            return; // the inventory changed under the click
        }
        Component name = stack.getHoverName();
        long total = value * count;
        inSlot.shrink(count);
        player.getInventory().setChanged();
        context.playerData().grantGold(player.getUUID(), total);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.6F, 0.9F);
        player.displayClientMessage(Component.translatable("message.eternalrealm.shop.sold", name, count, total)
                .withStyle(equipment.map(data -> data.rarity().rank() >= ItemRarity.RARE.rank()).orElse(false)
                        ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
    }
}
