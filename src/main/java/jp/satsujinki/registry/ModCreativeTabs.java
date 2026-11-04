package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Satsujinki.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.satsujinki"))
            .icon(() -> new ItemStack(ModItems.HOST_HAND.get()))
            .displayItems((params, out) -> ModItems.ITEMS.getEntries().forEach(ro -> out.accept(ro.get())))
            .build());

    private ModCreativeTabs() {
    }
}
