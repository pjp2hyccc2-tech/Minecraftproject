package com.hitori.registry;

import com.hitori.Hitori;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** One creative tab with every item and block of the mod (owner: game). */
public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Hitori.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hitori"))
            .icon(() -> {
                for (RegistryObject<Item> item : ModItems.ITEMS.getEntries()) return new ItemStack(item.get());
                return new ItemStack(Items.PAPER);
            })
            .displayItems((params, output) -> {
                for (RegistryObject<Item> item : ModItems.ITEMS.getEntries()) output.accept(item.get());
                for (RegistryObject<Item> item : ModBlocks.ITEMS.getEntries()) output.accept(item.get());
            })
            .build());

    private ModCreativeTab() {}
}
