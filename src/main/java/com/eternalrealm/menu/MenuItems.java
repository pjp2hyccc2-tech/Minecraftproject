package com.eternalrealm.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Icon items for server-side menus: a name, lore lines and an optional enchantment glint. */
public final class MenuItems {
    private MenuItems() {
    }

    public static ItemStack icon(Item item, Component name, List<Component> lore) {
        return icon(new ItemStack(item), name, lore, false);
    }

    public static ItemStack icon(ItemStack base, Component name, List<Component> lore, boolean glint) {
        ItemStack stack = base.copy();
        stack.setHoverName(name.copy().withStyle(style -> style.withItalic(false)));
        if (!lore.isEmpty()) {
            CompoundTag display = stack.getOrCreateTagElement("display");
            ListTag lines = new ListTag();
            for (Component line : lore) {
                Component plain = line.copy().withStyle(style -> style.withItalic(false)
                        .withColor(style.getColor() == null ? net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GRAY) : style.getColor()));
                lines.add(StringTag.valueOf(Component.Serializer.toJson(plain)));
            }
            display.put("Lore", lines);
        }
        if (glint) {
            stack.getOrCreateTag().putBoolean("eternalrealm_glint", true);
            stack.enchant(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING, 1);
            stack.getOrCreateTag().putInt("HideFlags", 127);
        }
        return stack;
    }

    /** A pane used as background/separator. */
    public static ItemStack filler() {
        return icon(new ItemStack(net.minecraft.world.item.Items.GRAY_STAINED_GLASS_PANE), Component.literal(" "),
                List.of(), false);
    }
}
