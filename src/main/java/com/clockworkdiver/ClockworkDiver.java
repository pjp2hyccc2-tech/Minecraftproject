package com.clockworkdiver;

import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.registry.ModBlockEntities;
import com.clockworkdiver.registry.ModBlocks;
import com.clockworkdiver.registry.ModCreativeTab;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import com.clockworkdiver.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Clockwork Diver: a steampunk clock repair adventure.
 * Customers bring strange clocks to the player's workshop. The player finds the faulty parts with the
 * Chrono Spectacles at the repair bench, shrinks down, dives inside the clock and repairs it in person.
 */
@Mod(ClockworkDiver.MODID)
public class ClockworkDiver {
    public static final String MODID = "clockworkdiver";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClockworkDiver() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(ModEntities::onAttributes);
        ModNetwork.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Clockwork Diver: the workshop bell is ready");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
