package com.birdiecraft;

import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.network.GolfNetwork;
import com.birdiecraft.registry.ModBlocks;
import com.birdiecraft.registry.ModEntities;
import com.birdiecraft.registry.ModItems;
import com.birdiecraft.registry.ModSounds;
import com.birdiecraft.registry.ModTabs;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * BirdieCraft - arcade golf for Minecraft Forge 1.20.1.
 */
@Mod(BirdieCraft.MODID)
public class BirdieCraft {
    public static final String MODID = "birdiecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BirdieCraft() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModTabs.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(ModEntities::onAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GolfConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GolfConfig.CLIENT_SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(GolfNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
