package jp.archiquest.registry;

import jp.archiquest.ArchiQuest;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ArchiQuest.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.archiquest"))
            .icon(() -> new ItemStack(ModItems.TEXTBOOK.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.TEXTBOOK.get());
                output.accept(ModItems.INSPECTOR.get());
                output.accept(ModItems.CERTIFICATE.get());
            })
            .build());

    private ModCreativeTabs() {}
}
