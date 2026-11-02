package com.meikon.item;

import com.meikon.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 菜種油: refills the lantern by half. */
public class OilItem extends LoreItem {
    public OilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack lantern = findLantern(player);
        if (lantern.isEmpty()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.meikon.no_lantern"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (LanternItem.oil(lantern) >= LanternItem.MAX_OIL) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.meikon.oil_full"), true);
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            LanternItem.setOil(lantern, LanternItem.oil(lantern) + LanternItem.MAX_OIL / 2);
            level.playSound(null, player.getX(), player.getY() + 1, player.getZ(), ModSounds.GAME_PAPER.get(), SoundSource.PLAYERS, 0.5f, 0.6f);
            player.displayClientMessage(Component.translatable("message.meikon.oil_refilled", LanternItem.oil(lantern) * 100 / LanternItem.MAX_OIL), true);
            if (!player.isCreative()) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    static ItemStack findLantern(Player player) {
        for (InteractionHand h : InteractionHand.values()) {
            ItemStack s = player.getItemInHand(h);
            if (s.getItem() instanceof LanternItem) return s;
        }
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof LanternItem) return s;
        }
        return ItemStack.EMPTY;
    }
}
