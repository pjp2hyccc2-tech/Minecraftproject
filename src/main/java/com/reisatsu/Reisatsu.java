package com.reisatsu;

import com.mojang.logging.LogUtils;
import com.reisatsu.game.GameFixtureListener;
import com.reisatsu.network.ModNetwork;
import com.reisatsu.registry.ModBlockEntities;
import com.reisatsu.registry.ModBlocks;
import com.reisatsu.registry.ModCreativeTab;
import com.reisatsu.registry.ModDimensions;
import com.reisatsu.registry.ModEntities;
import com.reisatsu.registry.ModItems;
import com.reisatsu.registry.ModParticles;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.FixtureEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 霊撮 ―REISATSU―: the family restaurant HIDAMARI by the deadly curve is haunted after closing. Sneak through it at
 * night with the medium's old instant camera and photograph every spirit without being noticed.
 */
@Mod(Reisatsu.MODID)
public class Reisatsu {
    public static final String MODID = "reisatsu";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Reisatsu() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModDimensions.CHUNK_GENERATORS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ReisatsuConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ReisatsuConfig.CLIENT_SPEC);
        ModNetwork.register();
        // the restaurant tells the game what happens in it
        FixtureEvents.LISTENER = new GameFixtureListener();
        LOGGER.info("[reisatsu] 霊撮: the automatic door opens. Nobody is there.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
