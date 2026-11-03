package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** The game's own items: camera, film, photos, flashlight, お札, keys (owner: game). */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Reisatsu.MODID);

    private ModItems() {}
}
