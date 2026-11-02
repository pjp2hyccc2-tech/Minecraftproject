package com.clockworkdiver.item;

import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.story.StoryQuest;
import com.clockworkdiver.story.StoryQuests;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 思い出の品: a keepsake each story client leaves with the player. Which one it is lives in its NBT. */
public class KeepsakeItem extends Item {
    public static final String TAG = "Story";

    public KeepsakeItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(StoryQuest quest) {
        ItemStack stack = new ItemStack(ModItems.KEEPSAKE.get());
        stack.getOrCreateTag().putString(TAG, quest.id);
        return stack;
    }

    @Nullable
    public static StoryQuest quest(ItemStack stack) {
        return stack.getTag() == null ? null : StoryQuests.byId(stack.getTag().getString(TAG));
    }

    /** Icon index for the item model. */
    public static float icon(ItemStack stack) {
        StoryQuest q = quest(stack);
        return q == null ? 0.0f : q.keepsake / (float) StoryQuests.KEEPSAKE_ICONS;
    }

    @Override
    public Component getName(ItemStack stack) {
        StoryQuest q = quest(stack);
        return q == null ? super.getName(stack) : Component.translatable(q.key("keepsake"));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        StoryQuest q = quest(stack);
        if (q == null) return;
        tooltip.add(Component.translatable("tooltip.clockworkdiver.keepsake.from", q.number, Component.translatable(q.titleKey()))
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable(q.key("keepsake.desc")).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        StoryQuest q = quest(stack);
        return q != null && q.number == StoryQuests.count();
    }
}
