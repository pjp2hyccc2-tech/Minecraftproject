package com.clockworkdiver.registry;

import com.clockworkdiver.ClockworkDiver;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ClockworkDiver.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.clockworkdiver"))
            .icon(() -> new ItemStack(ModItems.TINKER_WRENCH.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.WORKSHOP_KEY.get());
                output.accept(ModItems.CHRONO_SPECTACLES.get());
                output.accept(ModItems.TINKER_WRENCH.get());
                output.accept(ModItems.GEAR_TOOTH.get());
                output.accept(ModItems.BRASS_COIN.get());
                output.accept(ModItems.BOLT.get());
                output.accept(ModItems.REPAIR_BENCH.get());
                output.accept(ModItems.BRASS_BLOCK.get());
                output.accept(ModItems.BRASS_PLATE.get());
                output.accept(ModItems.BRASS_TILES.get());
                output.accept(ModItems.RUSTY_PLATE.get());
                output.accept(ModItems.COPPER_PIPE.get());
                output.accept(ModItems.JEWEL_BEARING.get());
                output.accept(ModItems.STEAM_VENT.get());
                output.accept(ModItems.RUST_CLUSTER.get());
                output.accept(ModItems.TOOTH_SOCKET.get());
                output.accept(ModItems.SCREW_HEAD.get());
                output.accept(ModItems.WINDING_CRANK.get());
                output.accept(ModItems.TUNING_BELL.get());
                output.accept(ModItems.DUST_PILE.get());
                output.accept(ModItems.MEMORY_COG.get());
                for (com.clockworkdiver.story.StoryQuest q : com.clockworkdiver.story.StoryQuests.all()) {
                    output.accept(com.clockworkdiver.item.KeepsakeItem.create(q));
                }
                output.accept(ModItems.CUSTOMER_SPAWN_EGG.get());
                output.accept(ModItems.RUST_MITE_SPAWN_EGG.get());
                output.accept(ModItems.COG_GREMLIN_SPAWN_EGG.get());
                output.accept(ModItems.DUST_MOTH_SPAWN_EGG.get());
                output.accept(ModItems.TIME_EATER_SPAWN_EGG.get());
            })
            .build());

    private ModCreativeTab() {}
}
