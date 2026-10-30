package com.nihonisshu.tabi.item;

import com.nihonisshu.tabi.game.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Local food and drinks. Eating one records it in the travel journal ("food.&lt;id&gt;"). */
public class TabiFoodItem extends TabiItem {
    private final boolean drink;
    private final String journalKey;

    public TabiFoodItem(Properties props, boolean drink, String journalKey) {
        super(props);
        this.drink = drink;
        this.journalKey = journalKey;
    }

    public String journalKey() {
        return journalKey;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return drink ? UseAnim.DRINK : UseAnim.EAT;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer sp && !journalKey.isEmpty()) {
            PlayerData.unlock(sp, journalKey);
            sp.displayClientMessage(Component.literal("「" + stack.getHoverName().getString() + "」をいただきました。"), true);
        }
        return super.finishUsingItem(stack, level, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
