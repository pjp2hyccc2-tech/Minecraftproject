package jp.archiquest.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** 模試合格証書（ゲーム内の記念アイテム） */
public class CertificateItem extends Item {
    public CertificateItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("Score")) {
            tooltip.add(Component.literal(t.getString("Owner")).withStyle(ChatFormatting.WHITE));
            tooltip.add(Component.literal(t.getInt("Correct") + "/" + t.getInt("Total") + "（" + t.getInt("Score") + "%）").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.literal(t.getString("Date")).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.archiquest.certificate.tooltip").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
