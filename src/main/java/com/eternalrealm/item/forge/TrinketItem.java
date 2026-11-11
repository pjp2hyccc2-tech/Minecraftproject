package com.eternalrealm.item.forge;

import com.eternalrealm.item.EquipmentBase;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;

import java.util.List;

/**
 * An accessory or a charm (装身具): the item the named trinkets are made on, one per tier and slot. Worn in the
 * trinket slots of the equipment screen ({@link TrinketSlots}); its stats live in the stack's equipment data.
 */
public final class TrinketItem extends Item {
    private final EquipmentBase base;

    public TrinketItem(EquipmentBase base, Properties properties) {
        super(properties);
        if (!base.isTrinket()) {
            throw new IllegalArgumentException("Not a trinket base: " + base);
        }
        this.base = base;
    }

    public EquipmentBase base() {
        return base;
    }

    @Override
    public Component getName(ItemStack stack) {
        return EquipmentStacks.displayName(stack).orElseGet(() -> super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        EquipmentStacks.appendTooltip(stack, tooltip);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return EquipmentStacks.isFoil(stack);
    }
}
