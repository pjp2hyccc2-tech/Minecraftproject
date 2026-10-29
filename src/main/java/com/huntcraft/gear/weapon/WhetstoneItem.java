package com.huntcraft.gear.weapon;

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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 砥石 Whetstone: never used up. Right click (or the sharpen key while holding a weapon) sharpens a weapon in about
 * 2 seconds (Speed Sharpening shortens it). Targets the weapon in hand, then the off hand, then the hotbar.
 */
public class WhetstoneItem extends Item {
    public WhetstoneItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) Sharpening.start((ServerPlayer) player);
        player.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.huntcraft.whetstone.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.huntcraft.weapon.whetstone_key").withStyle(ChatFormatting.DARK_AQUA));
    }
}
