package com.hitori.registry;

import com.hitori.Hitori;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** All blocks of the apartment and its street (owner: world). Block items are registered here too. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Hitori.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Hitori.MODID);

    private ModBlocks() {}

    /** Registers a block and its block item. */
    static <T extends Block> RegistryObject<T> block(String id, Supplier<T> block) {
        RegistryObject<T> b = BLOCKS.register(id, block);
        ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties()));
        return b;
    }
}
