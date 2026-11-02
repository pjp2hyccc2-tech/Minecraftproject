package com.birdiecraft.registry;

import com.birdiecraft.BirdieCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BirdieCraft.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.birdiecraft"))
            .icon(() -> new ItemStack(ModItems.GOLF_BALL.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.GOLF_CLUB.get());
                output.accept(ModItems.GOLF_BAG.get());
                output.accept(ModItems.COURSE_WAND.get());
                output.accept(ModItems.GOLF_BALL.get());
                for (RegistryObject<? extends Block> block : ModBlocks.WITH_ITEMS) {
                    output.accept(block.get());
                }
            })
            .build());

    private ModTabs() {
    }
}
