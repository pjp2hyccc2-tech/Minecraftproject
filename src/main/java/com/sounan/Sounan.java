package com.sounan;

import com.sounan.network.ModNetwork;
import com.sounan.registry.ModBlocks;
import com.sounan.registry.ModCreativeTab;
import com.sounan.registry.ModDimensions;
import com.sounan.registry.ModEntities;
import com.sounan.registry.ModItems;
import com.sounan.registry.ModParticles;
import com.sounan.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 遭難 ―SOUNAN―: lost on 帰らず山 at night with a phone that has no signal and a flashlight. Find your bag, keep
 * the mountain's rules, and get off the mountain before it keeps you.
 */
@Mod(Sounan.MODID)
public class Sounan {
    public static final String MODID = "sounan";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Sounan() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModDimensions.CHUNK_GENERATORS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SounanConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SounanConfig.CLIENT_SPEC);
        ModNetwork.register();
        LOGGER.info("[sounan] 遭難: somebody is calling from below the trail");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
