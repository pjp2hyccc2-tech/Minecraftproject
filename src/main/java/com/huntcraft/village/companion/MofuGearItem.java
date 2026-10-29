package com.huntcraft.village.companion;

import com.huntcraft.item.MaterialItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A piece of mofu companion equipment (weapon, helm or armor of one material tier). */
public class MofuGearItem extends Item {
    public final MofuGear.Slot slot;
    public final MofuGear.Tier tier;

    public MofuGearItem(Properties props, MofuGear.Slot slot, MofuGear.Tier tier) {
        super(props);
        this.slot = slot;
        this.tier = tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(MaterialItem.rarityColor(tier.rarity))));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.huntcraft.mofu_gear", slot.displayName()).withStyle(ChatFormatting.GOLD));
        for (Component c : statLines(slot, tier)) tooltip.add(c.copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.huntcraft.rarity", tier.rarity).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.huntcraft.mofu_gear.equip").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static List<Component> statLines(MofuGear.Slot slot, MofuGear.Tier tier) {
        return switch (slot) {
            case WEAPON -> tier.element == com.huntcraft.core.Element.NONE
                    ? List.of(Component.translatable("tooltip.huntcraft.mofu_gear.attack", fmt(tier.attack)))
                    : List.of(Component.translatable("tooltip.huntcraft.mofu_gear.attack", fmt(tier.attack)),
                    Component.translatable("tooltip.huntcraft.mofu_gear.element", tier.element.displayName(), fmt(tier.elemVal)));
            case HELM -> List.of(Component.translatable("tooltip.huntcraft.mofu_gear.defense", tier.helmDef));
            case ARMOR -> List.of(Component.translatable("tooltip.huntcraft.mofu_gear.defense", tier.armorDef),
                    Component.translatable("tooltip.huntcraft.mofu_gear.health", tier.armorHp));
        };
    }

    private static String fmt(float f) {
        return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.format(java.util.Locale.ROOT, "%.1f", f);
    }
}
