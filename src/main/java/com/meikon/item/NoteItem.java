package com.meikon.item;

import com.meikon.game.GameManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 手記: a page of a diary or a letter. Right-click to read. */
public class NoteItem extends LoreItem {
    public NoteItem(Properties properties) {
        super(properties);
    }

    public static int noteId(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt("NoteId") : 0;
    }

    public static ItemStack create(net.minecraft.world.item.Item item, int id) {
        ItemStack s = new ItemStack(item);
        s.getOrCreateTag().putInt("NoteId", id);
        return s;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("note.meikon." + noteId(stack) + ".title");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) GameManager.openNote(sp, noteId(stack));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.meikon.note.desc").withStyle(ChatFormatting.GRAY));
    }
}
