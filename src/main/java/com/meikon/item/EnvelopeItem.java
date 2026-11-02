package com.meikon.item;

import com.meikon.game.GameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 赤い封筒: a ghost-marriage invitation with your name on it. Opening it starts the game. */
public class EnvelopeItem extends LoreItem {
    public EnvelopeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) GameManager.openEnvelope(sp);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
