package com.sounan.item;

import com.sounan.client.ClientHooks;
import com.sounan.game.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/** Every usable item of the game. The behaviour lives in {@link GameManager#useItem} (server) and the client screens. */
public class GameItem extends Item {
    public enum Kind {
        FLASHLIGHT, FLASHLIGHT_ON, FLASHLIGHT_LOW, PHONE, PHONE_LIGHT, PHONE_CAMERA, BATTERY, POWER_BANK, BEAR_BELL, ROPE,
        TRAIL_MAP, FLARE, FLARE_LIT, OMAMORI, HUT_KEY, OMIKI, MEMO;

        public boolean flashlight() {
            return this == FLASHLIGHT || this == FLASHLIGHT_ON || this == FLASHLIGHT_LOW;
        }

        public boolean phone() {
            return this == PHONE || this == PHONE_LIGHT || this == PHONE_CAMERA;
        }

        public boolean flare() {
            return this == FLARE || this == FLARE_LIT;
        }
    }

    public final Kind kind;

    public GameItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public static Kind kindOf(ItemStack stack) {
        return stack.getItem() instanceof GameItem g ? g.kind : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            boolean sneak = player.isShiftKeyDown();
            if ((kind == Kind.PHONE || kind == Kind.PHONE_LIGHT) && !sneak) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openPhone(hand));
                return InteractionResultHolder.success(stack);
            }
            if (kind == Kind.TRAIL_MAP) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openMap);
                return InteractionResultHolder.success(stack);
            }
            return usable() ? InteractionResultHolder.success(stack) : InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer sp && GameManager.useItem(sp, hand, kind, player.isShiftKeyDown())) {
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private boolean usable() {
        return switch (kind) {
            case FLASHLIGHT, FLASHLIGHT_ON, FLASHLIGHT_LOW, PHONE, PHONE_LIGHT, PHONE_CAMERA, BATTERY, POWER_BANK, BEAR_BELL, FLARE -> true;
            default -> false;
        };
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (kind == Kind.FLARE_LIT && !level.isClientSide && entity instanceof ServerPlayer sp) {
            int burn = stack.getOrCreateTag().getInt("Burn") + 1;
            stack.getOrCreateTag().putInt("Burn", burn);
            if (burn >= GameManager.FLARE_TICKS) {
                stack.shrink(1);
                GameManager.flareBurnedOut(sp);
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged) return true;
        Kind a = kindOf(oldStack), b = kindOf(newStack);
        if (a == null || b == null) return true;
        if (a.flashlight() && b.flashlight()) return false;
        if (a.phone() && b.phone()) return false;
        if (a.flare() && b.flare()) return false;
        return !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return kind == Kind.BEAR_BELL && stack.isDamaged();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.sounan." + descKey() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    private String descKey() {
        return switch (kind) {
            case FLASHLIGHT, FLASHLIGHT_ON, FLASHLIGHT_LOW -> "flashlight";
            case PHONE, PHONE_LIGHT, PHONE_CAMERA -> "phone";
            case FLARE, FLARE_LIT -> "flare";
            default -> kind.name().toLowerCase(java.util.Locale.ROOT);
        };
    }
}
