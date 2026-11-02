package com.birdiecraft.item;

import com.birdiecraft.golf.engine.Club;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The golf club. Holding it enables the swing gauge (right click) and club switching
 * (mouse wheel). The selected club is stored in NBT and drives the item model.
 */
public class GolfClubItem extends Item {
    private static final String TAG_CLUB = "Club";

    public GolfClubItem(Properties properties) {
        super(properties);
    }

    public static Club getClub(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains(TAG_CLUB)) {
            return Club.byId(stack.getTag().getInt(TAG_CLUB));
        }
        return Club.DRIVER;
    }

    public static void setClub(ItemStack stack, Club club) {
        stack.getOrCreateTag().putInt(TAG_CLUB, club.ordinal());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // the swing gauge is driven entirely by the client input handler
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).append(" [" + getClub(stack).label + "]");
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.birdiecraft.club.1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.birdiecraft.club.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.birdiecraft.club.3").withStyle(ChatFormatting.GRAY));
    }
}
