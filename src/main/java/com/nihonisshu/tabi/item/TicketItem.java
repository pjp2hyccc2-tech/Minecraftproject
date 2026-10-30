package com.nihonisshu.tabi.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A paper train ticket: route + from/to stations. Collected when you get off at the destination. */
public class TicketItem extends Item {
    public TicketItem(Properties props) {
        super(props);
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("toName")) {
            return Component.literal("切符（" + t.getString("fromName") + " → " + t.getString("toName") + "）");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("toName")) {
            tooltip.add(Component.literal(t.getString("fromName") + " から " + t.getString("toName") + " まで有効").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.literal("運賃 " + t.getInt("fare") + "円").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.literal("駅の券売機で買えます").withStyle(ChatFormatting.GRAY));
        }
    }
}
