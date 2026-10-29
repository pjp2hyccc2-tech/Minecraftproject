package com.huntcraft.item;

import com.huntcraft.core.HCDimensions;
import com.huntcraft.core.HuntHooks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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

/** Farcaster: smoke escape back to the base camp during a quest. */
public class FarcasterItem extends Item {
    public FarcasterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!HCDimensions.isQuestMap(level)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.huntcraft.farcaster_no_quest"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            ((ServerLevel) level).sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.5, 1.0, 0.5, 0.02);
            HuntHooks.toBaseCamp.accept(sp);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.huntcraft.farcaster.desc").withStyle(ChatFormatting.GRAY));
    }
}
