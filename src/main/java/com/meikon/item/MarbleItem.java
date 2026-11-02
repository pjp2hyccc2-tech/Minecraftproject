package com.meikon.item;

import com.meikon.entity.Ohajiki;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** おはじき: glass marbles. Throw one — wherever it clatters, she goes to look. */
public class MarbleItem extends LoreItem {
    public MarbleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.4f, 1.6f);
        if (!level.isClientSide) {
            Ohajiki marble = new Ohajiki(level, player);
            marble.setItem(stack.copyWithCount(1));
            marble.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.3f, 1.0f);
            level.addFreshEntity(marble);
        }
        player.getCooldowns().addCooldown(this, 15);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
