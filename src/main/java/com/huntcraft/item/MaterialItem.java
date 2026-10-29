package com.huntcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Monster / gathering material with MH-style rarity (1..8) colouring. */
public class MaterialItem extends Item {
    public static final int[] RARITY_COLORS = {0xFFFFFF, 0xFFFFFF, 0xD9CCFF, 0xA8E07A, 0x7AD7F0, 0x6E8BFF, 0xFF9DE0, 0xFF6A6A, 0xFFB347};

    private final int hcRarity;

    public MaterialItem(Properties props, int rarity) {
        super(props);
        this.hcRarity = Math.max(1, Math.min(8, rarity));
    }

    public int getHcRarity() {
        return hcRarity;
    }

    public static int rarityColor(int rarity) {
        return RARITY_COLORS[Math.max(0, Math.min(RARITY_COLORS.length - 1, rarity))];
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rarityColor(hcRarity))));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.huntcraft.rarity", hcRarity).withStyle(ChatFormatting.GRAY));
        String descKey = getDescriptionId() + ".desc";
        tooltip.add(Component.translatable(descKey).withStyle(ChatFormatting.DARK_GRAY));
    }
}
