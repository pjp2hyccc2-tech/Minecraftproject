package com.eternalrealm.client;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.CombatStats;
import com.eternalrealm.hud.HudLayout;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.ConsumableEffect;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.UseItemPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The quick-use items on the number keys 1-3 (vanilla's hotbar keys; there is no hotbar): 1 drinks a health potion,
 * 2 a stamina tonic, 3 reads a return scroll. For 1 the client picks the smallest potion that makes up the missing
 * health (VIT bonus included), or the strongest one the character may use when none does. The client only names the
 * inventory slot; the server checks and uses it ({@link UseItemPacket}).
 */
public final class QuickSlots {
    public static final int COUNT = HudLayout.QUICK_SLOTS;
    /** What each slot uses, weakest first. */
    private static final List<List<Consumable>> KINDS = List.of(
            List.of(Consumable.HEALTH_POTION_SMALL, Consumable.HEALTH_POTION_MEDIUM, Consumable.HEALTH_POTION_LARGE),
            List.of(Consumable.STAMINA_TONIC),
            List.of(Consumable.RETURN_SCROLL));

    /**
     * What a slot would use now.
     *
     * @param inventorySlot where the item is, -1 when the player has none
     * @param shown         the item to draw, its count the total the player carries (0 when none)
     */
    public record Pick(int inventorySlot, ItemStack shown) {
    }

    private QuickSlots() {
    }

    public static Pick pick(LocalPlayer player, int quick) {
        List<Consumable> kinds = KINDS.get(quick);
        int level = ClientCharacterState.snapshot().map(CharacterSnapshot::level).orElse(1);
        Consumable chosen = null;
        if (quick == 0) {
            double missing = Math.max(0.0, player.getMaxHealth() - player.getHealth());
            CombatStats stats = ClientCombatState.stats();
            double bonus = stats == null ? 0.0 : stats.potionBonus();
            for (Consumable kind : kinds) {
                if (kind.requiredLevel() > level || count(player, kind) == 0) {
                    continue;
                }
                chosen = kind; // the strongest usable so far
                double heals = ((ConsumableEffect.Heal) kind.effect()).amount(player.getMaxHealth()) * (1.0 + bonus);
                if (heals >= missing) {
                    break;
                }
            }
        } else {
            for (Consumable kind : kinds) {
                if (kind.requiredLevel() <= level && count(player, kind) > 0) {
                    chosen = kind;
                }
            }
        }
        if (chosen == null) {
            return new Pick(-1, new ItemStack(ERGearItems.consumable(kinds.get(0)), 1));
        }
        Item item = ERGearItems.consumable(chosen);
        int slot = -1;
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i).is(item)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            slot = ClientBagBack.find(item); // waiting in the bag's back: the server brings it forward
        }
        return new Pick(slot, new ItemStack(item, count(player, chosen)));
    }

    /** The key of quick slot {@code quick} was pressed (no screen open). */
    public static void use(int quick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || quick < 0 || quick >= COUNT) {
            return;
        }
        Pick pick = pick(player, quick);
        if (pick.inventorySlot() < 0) {
            player.displayClientMessage(Component.translatable("hud.eternalrealm.quick.none_" + quick)
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (player.getCooldowns().isOnCooldown(pick.shown().getItem())) {
            return; // the slot shows the cooldown already
        }
        ERNetwork.sendToServer(new UseItemPacket(pick.inventorySlot()));
    }

    private static int count(LocalPlayer player, Consumable kind) {
        Item item = ERGearItems.consumable(kind);
        return player.getInventory().countItem(item) + ClientBagBack.count(item);
    }
}
