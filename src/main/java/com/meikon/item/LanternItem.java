package com.meikon.item;

import com.meikon.registry.ModItems;
import com.meikon.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 提灯: the paper lantern. Right-click lights / blows it out (the lit and unlit lanterns are two items so shader
 * packs can tell them apart). It burns oil while lit; held in either hand it lights the way — and shows you to her.
 */
public class LanternItem extends Item {
    public static final int MAX_OIL = 1000;
    /** Ticks per oil unit: a full lantern burns for 10 minutes. */
    public static final int BURN_INTERVAL = 12;
    private final boolean lit;

    public LanternItem(Properties properties, boolean lit) {
        super(properties);
        this.lit = lit;
    }

    public boolean isLit() {
        return lit;
    }

    public static int oil(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains("Oil") ? stack.getTag().getInt("Oil") : MAX_OIL * 6 / 10;
    }

    public static void setOil(ItemStack stack, int oil) {
        stack.getOrCreateTag().putInt("Oil", Mth.clamp(oil, 0, MAX_OIL));
    }

    /** Returns a copy of the stack as the other (lit/unlit) lantern item, keeping the oil. */
    public static ItemStack toggled(ItemStack stack, boolean toLit) {
        ItemStack out = new ItemStack(toLit ? ModItems.CHOCHIN_LIT.get() : ModItems.CHOCHIN.get());
        if (stack.hasTag()) out.setTag(stack.getTag().copy());
        setOil(out, oil(stack));
        return out;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!lit && oil(stack) <= 0) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.meikon.no_oil"), true);
            return InteractionResultHolder.fail(stack);
        }
        ItemStack next = toggled(stack, !lit);
        level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                lit ? ModSounds.GAME_LANTERN_OFF.get() : ModSounds.GAME_LANTERN_ON.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        player.getCooldowns().addCooldown(ModItems.CHOCHIN.get(), 8);
        player.getCooldowns().addCooldown(ModItems.CHOCHIN_LIT.get(), 8);
        return InteractionResultHolder.sidedSuccess(next, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!lit || level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;
        if (level.getGameTime() % BURN_INTERVAL == 0) {
            int oil = oil(stack) - 1;
            setOil(stack, oil);
            if (oil <= 0) {
                ItemStack out = toggled(stack, false);
                if (slot == -1 || player.getInventory().getItem(slot) != stack) {
                    // offhand
                    if (player.getOffhandItem() == stack) player.setItemInHand(InteractionHand.OFF_HAND, out);
                } else {
                    player.getInventory().setItem(slot, out);
                }
                level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), ModSounds.GAME_LANTERN_OFF.get(),
                        SoundSource.PLAYERS, 0.8f, 0.8f);
                player.displayClientMessage(Component.translatable("message.meikon.oil_out"), true);
            }
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0f * oil(stack) / MAX_OIL);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float f = oil(stack) / (float) MAX_OIL;
        return Mth.color(0.9f, 0.35f + 0.4f * f, 0.1f);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.meikon.chochin.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.meikon.chochin.oil", oil(stack) * 100 / MAX_OIL).withStyle(ChatFormatting.GOLD));
    }
}
