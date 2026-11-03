package com.hitori.registry;

import com.hitori.Hitori;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** The game's own items: the ritual items, cameras, laptop, phone, lighter (owner: game). */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Hitori.MODID);

    private ModItems() {}
}
