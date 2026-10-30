package com.mcbesiege;

import com.mcbesiege.net.Net;
import com.mcbesiege.registry.ModBlockEntities;
import com.mcbesiege.registry.ModBlocks;
import com.mcbesiege.registry.ModEntities;
import com.mcbesiege.registry.ModItems;
import com.mcbesiege.registry.ModTabs;
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
 * MINECRAFT BESIEGE — build × fight × destroy. A Besiege-style physics siege machine builder for Forge 1.20.1.
 */
@Mod(MCBesiege.MODID)
public class MCBesiege {
    public static final String MODID = "mcbesiege";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCBesiege() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModTabs.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("[mcbesiege] MINECRAFT BESIEGE loading");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::init);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
