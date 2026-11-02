package com.sounan.registry;

import com.sounan.Sounan;
import com.sounan.item.NoteItem;
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
            .title(Component.translatable("itemGroup.meikon"))
            .icon(() -> new ItemStack(ModItems.RED_ENVELOPE.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.RED_ENVELOPE.get());
                output.accept(ModItems.CHOCHIN.get());
                output.accept(ModItems.CHOCHIN_LIT.get());
                output.accept(ModItems.LAMP_OIL.get());
                output.accept(ModItems.SUZU.get());
                output.accept(ModItems.SHIO.get());
                output.accept(ModItems.OFUDA.get());
                output.accept(ModItems.OHAJIKI.get());
                output.accept(ModItems.KURA_KEY.get());
                output.accept(ModItems.DOLL_HEAD.get());
                output.accept(ModItems.MATCHES.get());
                output.accept(ModItems.OMAMORI.get());
                output.accept(ModItems.ASH.get());
                for (int i = 1; i <= com.sounan.game.Notes.COUNT; i++) output.accept(NoteItem.create(ModItems.NOTE.get(), i));
                output.accept(ModItems.HANAYOME_SPAWN_EGG.get());
                output.accept(ModItems.ICHIMATSU_SPAWN_EGG.get());
                output.accept(ModItems.KAGEBOSHI_SPAWN_EGG.get());
                for (RegistryObject<Item> item : ModItems.BLOCK_ITEMS) output.accept(item.get());
            })
            .build());

    private ModCreativeTab() {}
}
