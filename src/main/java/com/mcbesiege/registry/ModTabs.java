package com.mcbesiege.registry;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.part.PartType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModTabs {
    private ModTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MCBesiege.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcbesiege.main"))
            .icon(() -> new ItemStack(ModItems.part(PartType.STARTING_BLOCK)))
            .displayItems((params, out) -> {
                out.accept(ModItems.WRENCH.get());
                out.accept(ModItems.BRACE_TOOL.get());
                out.accept(ModItems.SPRING_TOOL.get());
                out.accept(ModItems.ROPE_TOOL.get());
                out.accept(ModItems.BLUEPRINT.get());
                out.accept(ModItems.MISSION_TABLE.get());
                for (PartType.Cat c : PartType.Cat.values()) {
                    for (PartType t : PartType.values()) if (t.cat == c) out.accept(ModItems.part(t));
                }
            })
            .build());

    public static final RegistryObject<CreativeModeTab> FORTRESS = TABS.register("fortress", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mcbesiege.fortress"))
            .icon(() -> new ItemStack(ModItems.FORTRESS.get(0).get()))
            .displayItems((params, out) -> {
                for (RegistryObject<Item> i : ModItems.FORTRESS) out.accept(i.get());
                out.accept(ModItems.CANNONBALL.get());
            })
            .build());
}
