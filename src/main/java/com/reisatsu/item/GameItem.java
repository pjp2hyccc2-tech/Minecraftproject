package com.reisatsu.item;

import com.reisatsu.client.ClientHooks;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Every item of the night. The behaviour lives in {@link GameManager#useItem} (server) and the client screens.
 * Camera NBT: {@code film}. Photo NBT: {@code photo} (id of the PNG), {@code spirit}, {@code stars}, {@code time}.
 */
public class GameItem extends Item {
    public enum Kind {
        CAMERA, CAMERA_FINDER, FILM_PACK, PHOTO, FLASHLIGHT, FLASHLIGHT_ON, OFUDA, SHOP_KEY, OFFICE_KEY;

        public boolean camera() {
            return this == CAMERA || this == CAMERA_FINDER;
        }

        public boolean flashlight() {
            return this == FLASHLIGHT || this == FLASHLIGHT_ON;
        }
    }

    public static final int START_FILM = 10;
    public static final int FILM_PER_PACK = 5;

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
    public static int film(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null && t.contains("film") ? t.getInt("film") : START_FILM;
    }

    public static void setFilm(ItemStack stack, int film) {
        stack.getOrCreateTag().putInt("film", Math.max(0, film));
    }

    public static String photoId(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null ? t.getString("photo") : "";
    }

    @Nullable
    public static SpiritKind photoSpirit(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null ? SpiritKind.byId(t.getString("spirit")) : null;
    }

    public static int photoStars(ItemStack stack) {
        CompoundTag t = stack.getTag();
        return t != null ? t.getInt("stars") : 0;
    }

    public static void setPhoto(ItemStack stack, String id, SpiritKind spirit, int stars, long time) {
        CompoundTag t = stack.getOrCreateTag();
        t.putString("photo", id);
        t.putString("spirit", spirit.id);
        t.putInt("stars", stars);
        t.putLong("time", time);
    }

    // ------------------------------------------------------------------------------------------------ use
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (kind == Kind.PHOTO) {
                String id = photoId(stack);
                SpiritKind s = photoSpirit(stack);
                int stars = photoStars(stack);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openPhoto(id, s, stars));
                return InteractionResultHolder.success(stack);
            }
            return usable() ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer sp && GameManager.useItem(sp, hand, kind)) {
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    private boolean usable() {
        return switch (kind) {
            case CAMERA, CAMERA_FINDER, FLASHLIGHT, FLASHLIGHT_ON, FILM_PACK -> true;
            default -> false;
        };
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (slotChanged) return true;
        Kind a = kindOf(oldStack), b = kindOf(newStack);
        if (a == null || b == null) return true;
        if (a.camera() && b.camera()) return false;
        if (a.flashlight() && b.flashlight()) return false;
        return !ItemStack.isSameItem(oldStack, newStack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        switch (kind) {
            case CAMERA, CAMERA_FINDER -> {
                tooltip.add(Component.translatable("item.reisatsu.reisha_camera.film", film(stack)).withStyle(ChatFormatting.GRAY));
                tooltip.add(Component.translatable("item.reisatsu.reisha_camera.desc").withStyle(ChatFormatting.DARK_GRAY));
            }
            case PHOTO -> {
                SpiritKind s = photoSpirit(stack);
                if (s != null) {
                    tooltip.add(Component.translatable(s.nameKey()).withStyle(ChatFormatting.DARK_RED));
                    tooltip.add(Component.literal("★".repeat(Math.max(1, Math.min(3, photoStars(stack))))).withStyle(ChatFormatting.GOLD));
                }
                tooltip.add(Component.translatable("item.reisatsu.evidence_photo.desc").withStyle(ChatFormatting.DARK_GRAY));
            }
            case FLASHLIGHT, FLASHLIGHT_ON -> tooltip.add(Component.translatable("item.reisatsu.flashlight.desc").withStyle(ChatFormatting.GRAY));
            default -> tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
