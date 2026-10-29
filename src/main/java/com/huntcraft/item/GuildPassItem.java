package com.huntcraft.item;

import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HuntHooks;
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

/** Guild pass: travel between the overworld and the hunter village. */
public class GuildPassItem extends Item {
    public GuildPassItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (HCDimensions.isQuestMap(level)) {
            player.displayClientMessage(Component.translatable("message.huntcraft.pass_in_quest"), true);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, 60);
        if (HCDimensions.isVillage(level)) HuntHooks.toOverworld.accept(sp);
        else HuntHooks.toVillage.accept(sp);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.huntcraft.guild_pass.desc").withStyle(ChatFormatting.GRAY));
    }
}
