package com.sounan.registry;

import com.sounan.Sounan;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Sounan.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.sounan"))
            .icon(() -> new ItemStack(ModItems.FLASHLIGHT_ON.get()))
            .displayItems((params, output) -> {
                for (RegistryObject<Item> item : ModItems.GAME_ITEMS) output.accept(item.get());
                output.accept(ModItems.NOZOKI_SPAWN_EGG.get());
                output.accept(ModItems.YOBIGOE_SPAWN_EGG.get());
                output.accept(ModItems.USHIRO_SPAWN_EGG.get());
                output.accept(ModItems.KOYANUSHI_SPAWN_EGG.get());
                output.accept(ModItems.OYAMASAMA_SPAWN_EGG.get());
                output.accept(ModItems.UTSURUMONO_SPAWN_EGG.get());
                for (RegistryObject<Item> item : ModItems.BLOCK_ITEMS) output.accept(item.get());
            })
            .build());

    private ModCreativeTab() {}
}
