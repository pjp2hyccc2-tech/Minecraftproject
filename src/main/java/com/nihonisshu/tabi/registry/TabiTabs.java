package com.nihonisshu.tabi.registry;

import com.nihonisshu.tabi.TabiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class TabiTabs {
    private TabiTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TabiMod.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tabi"))
            .icon(() -> new ItemStack(TabiItems.JOURNAL.get()))
            .displayItems((params, output) -> {
                for (RegistryObject<Item> item : TabiItems.ITEMS.getEntries()) {
                    output.accept(item.get());
                }
            })
            .build());
}
