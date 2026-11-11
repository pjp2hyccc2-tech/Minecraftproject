package com.eternalrealm.mount.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mount module's items: the deed of a kept horse or griffin ({@code eternalrealm:mount_deed}). */
public final class MountItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    public static final RegistryObject<MountDeedItem> DEED = ITEMS.register("mount_deed",
            () -> new MountDeedItem(new Item.Properties().stacksTo(1)));

    private MountItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
