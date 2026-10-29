package com.huntcraft.core;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Items that can be levelled up at the blacksmith (weapons and armor). Level is stored in the stack NBT
 * ("HCLevel", starting at 1).
 */
public interface IUpgradeable {
    String LEVEL_TAG = "HCLevel";

    default int getLevel(ItemStack stack) {
        return stack.hasTag() ? Math.max(1, stack.getTag().getInt(LEVEL_TAG)) : 1;
    }

    default void setLevel(ItemStack stack, int level) {
        stack.getOrCreateTag().putInt(LEVEL_TAG, level);
    }

    int getMaxLevel(ItemStack stack);

    /** Materials needed to go from current level to `nextLevel`. */
    List<SmithingRegistry.Cost> getUpgradeCost(ItemStack stack, int nextLevel);

    int getUpgradeZenny(ItemStack stack, int nextLevel);

    /** Hunter rank required for `nextLevel` (levels above 5 usually need high rank). */
    default int getRequiredHR(ItemStack stack, int nextLevel) {
        return nextLevel > 5 ? 8 : 1;
    }
}
