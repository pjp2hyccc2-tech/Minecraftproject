package com.nihonisshu.tabi.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Plain item with a descriptive tooltip ("item.tabi.x.desc" translation, lines split by \n). */
public class TabiItem extends Item {
    public TabiItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        addDesc(getDescriptionId(), tooltip);
    }

    public static void addDesc(String descriptionId, List<Component> tooltip) {
        String text = Component.translatable(descriptionId + ".desc").getString();
        if (text.equals(descriptionId + ".desc")) return;
        for (String line : text.split("\n")) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
    }
}
