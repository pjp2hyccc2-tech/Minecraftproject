package com.eternalrealm.combat.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** The "ETERNAL REALM" creative tab. */
public final class ERCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ModInfo.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + ModInfo.MOD_ID))
            .icon(() -> new ItemStack(ERItems.IRON_LONGSWORD.get()))
            .displayItems((parameters, output) -> ERItems.weapons().forEach(weapon -> output.accept(weapon.get())))
            .build());

    private ERCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
