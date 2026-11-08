package com.eternalrealm.item.forge;

import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** An ETERNAL REALM armor piece: its stats live in the stack's equipment data (see {@link EquipmentStacks}). */
public final class GearArmorItem extends ArmorItem {
    private final EquipmentBase base;

    public GearArmorItem(EquipmentBase base, Properties properties) {
        super(ERArmorMaterials.of(base.tier()), typeOf(base.slot()), properties);
        this.base = base;
    }

    public EquipmentBase base() {
        return base;
    }

    static ArmorItem.Type typeOf(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorItem.Type.HELMET;
            case CHEST -> ArmorItem.Type.CHESTPLATE;
            case LEGS -> ArmorItem.Type.LEGGINGS;
            case FEET -> ArmorItem.Type.BOOTS;
            case WEAPON -> throw new IllegalArgumentException("A weapon is not armor");
        };
    }

    /** The vanilla equipment slot of an armor slot. */
    public static net.minecraft.world.entity.EquipmentSlot vanillaSlot(EquipmentSlot slot) {
        return typeOf(slot).getSlot();
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
