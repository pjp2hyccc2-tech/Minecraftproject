package com.huntcraft.registry;

import com.huntcraft.HuntCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Creative tabs. Modules add their items with BuildCreativeModeTabContentsEvent, e.g.
 * <pre>if (event.getTabKey() == HCCreativeTabs.WEAPONS.getKey()) event.accept(MY_ITEM);</pre>
 */
public final class HCCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HuntCraft.MODID);

    private HCCreativeTabs() {
    }

    public static final RegistryObject<CreativeModeTab> WEAPONS = TABS.register("weapons", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.huntcraft.weapons"))
            .icon(() -> new ItemStack(Items.IRON_SWORD))
            .build());

    public static final RegistryObject<CreativeModeTab> ARMOR = TABS.register("armor", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.huntcraft.armor"))
            .icon(() -> new ItemStack(Items.IRON_CHESTPLATE))
            .build());

    public static final RegistryObject<CreativeModeTab> MATERIALS = TABS.register("materials", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.huntcraft.materials"))
            .icon(() -> new ItemStack(HCItems.IGNIVALD.scale.get()))
            .displayItems((params, output) -> HCItems.MATERIALS.forEach(ro -> output.accept(ro.get())))
            .build());

    public static final RegistryObject<CreativeModeTab> ITEMS = TABS.register("items", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.huntcraft.items"))
            .icon(() -> new ItemStack(HCItems.POTION.get()))
            .displayItems((params, output) -> HCItems.CONSUMABLES.forEach(ro -> output.accept(ro.get())))
            .build());

    public static final RegistryObject<CreativeModeTab> MONSTERS = TABS.register("monsters", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.huntcraft.monsters"))
            .icon(() -> new ItemStack(Items.DRAGON_HEAD))
            .build());
}
