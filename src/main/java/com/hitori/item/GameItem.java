package com.hitori.item;

import com.hitori.client.ClientHooks;
import com.hitori.game.Cameras;
import com.hitori.game.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Every item of the night (docs/DESIGN.md §7). The ritual's behaviour lives in {@code game/Ritual} (server), the
 * laptop, the phone and the printout open client screens. NBT: {@code nails} (the plush with your nail clippings in
 * it), {@code name} (the doll's name), {@code spat} (salt water spat on it, the ending ritual), {@code sips} (salt
 * water left in the cup).
 */
public class GameItem extends Item {
    public enum Kind {
        PLUSH, PLUSH_OPEN, PLUSH_RICE, RITUAL_DOLL, SCISSORS, RICE_SCOOP, RED_THREAD, NAIL_CLIPPER, KNIFE, SALT, CUP_WATER,
        SALT_WATER, FIXED_CAMERA, LAPTOP, PHONE, PHONE_LIGHT, LIGHTER, PRINTOUT;

        public boolean phone() {
            return this == PHONE || this == PHONE_LIGHT;
        }

        public boolean screen() {
            return this == LAPTOP || phone();
        }
    }

    /** Mouthfuls of salt water in a full cup. */
    public static final int CUP_SIPS = 4;

    public final Kind kind;

    public GameItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Nullable
    public static Kind kindOf(ItemStack stack) {
        return stack.getItem() instanceof GameItem g ? g.kind : null;
    }

    public static boolean is(ItemStack stack, Kind kind) {
        return kindOf(stack) == kind;
    }

    // ------------------------------------------------------------------------------------------------ NBT
    public static boolean hasNails(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.getBoolean("nails");
    }

    public static void setNails(ItemStack stack) {
        stack.getOrCreateTag().putBoolean("nails", true);
    }

    public static int sips(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains("sips") ? t.getInt("sips") : CUP_SIPS;
    }

    public static void setSips(ItemStack stack, int n) {
        stack.getOrCreateTag().putInt("sips", Math.max(0, n));
    }

    public static String dollName(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null ? t.getString("name") : "";
    }

    public static void setDollName(ItemStack stack, String name) {
        stack.getOrCreateTag().putString("name", name);
    }

    public static boolean spat(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.getBoolean("spat");
    }

    public static void setSpat(ItemStack stack) {
        stack.getOrCreateTag().putBoolean("spat", true);
    }

    // ------------------------------------------------------------------------------------------------ use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            switch (kind) {
                case LAPTOP -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openCameraApp(0));
                    return InteractionResultHolder.success(stack);
                }
                case PHONE, PHONE_LIGHT -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openPhone(player.isShiftKeyDown()));
                    return InteractionResultHolder.success(stack);
                }
                case PRINTOUT -> {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openPrintout());
                    return InteractionResultHolder.success(stack);
                }
                default -> {
                    return usable() ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
                }
            }
        }
        if (player instanceof ServerPlayer sp && GameManager.useItem(sp, hand, kind)) {
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (kind != Kind.FIXED_CAMERA || ctx.getPlayer() == null) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer sp && Cameras.place(sp, ctx)) return InteractionResult.CONSUME;
        return InteractionResult.FAIL;
    }

    private boolean usable() {
        return switch (kind) {
            case SALT_WATER, NAIL_CLIPPER, RED_THREAD, RICE_SCOOP, SALT, LIGHTER, CUP_WATER -> true;
            default -> false;
        };
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged) return true;
        Kind a = kindOf(oldStack), b = kindOf(newStack);
        if (a == null || b == null) return true;
        if (a.phone() && b.phone()) return false;
        if (a == b) return false;
        return !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (kind == Kind.RITUAL_DOLL) {
            String name = dollName(stack);
            if (!name.isEmpty()) return Component.translatable("item.hitori.ritual_doll.named", name);
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        switch (kind) {
            case PLUSH_RICE -> {
                if (hasNails(stack)) tooltip.add(Component.translatable("item.hitori.plush_rice.nails").withStyle(ChatFormatting.DARK_RED));
                tooltip.add(Component.translatable("item.hitori.plush_rice.desc").withStyle(ChatFormatting.GRAY));
            }
            case SALT_WATER -> {
                tooltip.add(Component.translatable("item.hitori.salt_water.sips", sips(stack)).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("item.hitori.salt_water.desc").withStyle(ChatFormatting.DARK_GRAY));
            }
            case PHONE_LIGHT -> tooltip.add(Component.translatable("item.hitori.phone.desc").withStyle(ChatFormatting.GRAY));
            default -> tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
