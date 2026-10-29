package com.huntcraft.item;

import com.huntcraft.entity.misc.HCThrownItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Paintball / flash bomb / dung bomb / tranq bomb. */
public class ThrowableToolItem extends Item {
    public enum Kind { PAINT, FLASH, DUNG, TRANQ }

    public final Kind kind;

    public ThrowableToolItem(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.6F);
        if (!level.isClientSide) {
            HCThrownItem proj = new HCThrownItem(level, player);
            ItemStack copy = stack.copy();
            copy.setCount(1);
            proj.setItem(copy);
            proj.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.4F, 0.6F);
            level.addFreshEntity(proj);
        }
        player.getCooldowns().addCooldown(this, 10);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
