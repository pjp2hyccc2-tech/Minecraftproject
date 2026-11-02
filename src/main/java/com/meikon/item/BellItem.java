package com.meikon.item;

import com.meikon.game.ClientView;
import com.meikon.game.GameManager;
import com.meikon.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 鈴: one of the seven bells of the wedding procession. Hold right-click at the torii with all seven to sever the bond. */
public class BellItem extends LoreItem {
    public static final int RITUAL_TICKS = 100;

    public BellItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean ready = level.isClientSide ? ClientView.ritualReady : player instanceof ServerPlayer sp && GameManager.canStartRitual(sp);
        if (!ready) {
            level.playSound(player, player.getX(), player.getY() + 1, player.getZ(), ModSounds.GAME_BELL_CHIME.get(), SoundSource.PLAYERS, 0.35f, 1.1f);
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer sp) GameManager.ritualStarted(sp);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return RITUAL_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) {
            if (!GameManager.canStartRitual(sp)) {
                sp.stopUsingItem();
                return;
            }
            GameManager.ritualTick(sp, RITUAL_TICKS - remaining);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) GameManager.ritualComplete(sp);
        return stack;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!level.isClientSide && entity instanceof ServerPlayer sp) GameManager.ritualInterrupted(sp);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.meikon.suzu.desc2").withStyle(ChatFormatting.DARK_RED));
    }
}
