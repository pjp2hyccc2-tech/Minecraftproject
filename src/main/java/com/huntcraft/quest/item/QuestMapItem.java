package com.huntcraft.quest.item;

import com.huntcraft.network.HCNetwork;
import com.huntcraft.quest.QuestManager;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** 地図: supply item; opens the full map of the current quest region (areas, camp, party, painted monsters). */
public class QuestMapItem extends Item {
    public QuestMapItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (QuestManager.instanceOf(sp) == null) {
                sp.displayClientMessage(Component.translatable("message.huntcraft.quest.map_no_quest"), true);
            } else {
                HCNetwork.sendToPlayer(sp, "quest/open_map", new CompoundTag());
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.huntcraft.quest_map.desc").withStyle(ChatFormatting.GRAY));
    }
}
