package com.clockworkdiver.item;

import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.workshop.WorkshopManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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

/** Opens the way to the player's clock repair workshop and back. */
public class WorkshopKeyItem extends Item {
    public WorkshopKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (ModDimensions.isInterior(level)) {
                sp.displayClientMessage(Component.translatable("message.clockworkdiver.key_inside"), true);
            } else if (ModDimensions.isWorkshop(level)) {
                WorkshopManager.leaveWorkshop(sp);
            } else {
                WorkshopManager.enterWorkshop(sp);
            }
            sp.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.clockworkdiver.key.0").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.clockworkdiver.key.1").withStyle(ChatFormatting.GRAY));
    }
}
