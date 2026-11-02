package com.clockworkdiver.item;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.clock.Defect;
import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A customer's clock. All the commission data lives in its NBT ({@link ClockData}). */
public class BrokenClockItem extends Item {
    public BrokenClockItem(Properties properties) {
        super(properties);
    }

    /** Index of the clock kind, used by the item model to pick a texture. */
    public static float typeIndex(ItemStack stack) {
        ClockData d = ClockData.fromStack(stack);
        return d == null ? 0.0f : d.type.ordinal();
    }

    @Override
    public Component getName(ItemStack stack) {
        ClockData d = ClockData.fromStack(stack);
        if (d == null) return super.getName(stack);
        return Component.translatable(d.allRepaired() ? "item.clockworkdiver.broken_clock.repaired" : "item.clockworkdiver.broken_clock.named",
                Component.translatable(d.nameKey()));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        ClockData d = ClockData.fromStack(stack);
        return d != null && d.allRepaired();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ClockData d = ClockData.fromStack(stack);
        if (d == null) {
            tooltip.add(Component.translatable("tooltip.clockworkdiver.clock.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.clockworkdiver.clock.owner", Component.translatable(d.profile.nameKey()))
                .withStyle(ChatFormatting.GOLD));
        for (Defect def : d.defects) {
            MutableComponent line = Component.literal(" ");
            if (def.type == com.clockworkdiver.clock.DefectType.CLUE) {
                if (!def.found) continue;
                line.append(Component.translatable(d.isStory() ? "story.clockworkdiver." + d.story + ".clue" : "defect.clockworkdiver.clue"));
                tooltip.add(line.withStyle(ChatFormatting.LIGHT_PURPLE));
                continue;
            }
            line.append(def.found ? Component.translatable(def.partKey()) : Component.literal("???"));
            line.append(" ");
            line.append(Component.translatable(def.repaired ? "tooltip.clockworkdiver.clock.fixed"
                    : def.found ? "tooltip.clockworkdiver.clock.broken" : "tooltip.clockworkdiver.clock.unknown"));
            tooltip.add(line.withStyle(def.repaired ? ChatFormatting.GREEN : def.found ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(d.allRepaired() ? "tooltip.clockworkdiver.clock.deliver" : "tooltip.clockworkdiver.clock.use")
                .withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            WorkshopManager.promptRepair(sp);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
