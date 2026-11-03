package com.hitori;

import com.mojang.logging.LogUtils;
import com.hitori.game.GameFixtureListener;
import com.hitori.network.ModNetwork;
import com.hitori.registry.ModBlockEntities;
import com.hitori.registry.ModBlocks;
import com.hitori.registry.ModCreativeTab;
import com.hitori.registry.ModDimensions;
import com.hitori.registry.ModEntities;
import com.hitori.registry.ModItems;
import com.hitori.registry.ModParticles;
import com.hitori.registry.ModSounds;
import com.hitori.world.FixtureEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * ひとりかくれんぼ ―HITORI KAKURENBO―: perform the one-person hide-and-seek ritual alone in apartment 203 at 3 a.m.,
 * hide in the closet with salt water in your mouth, and watch the doll search for you through your fixed cameras.
 */
@Mod(Hitori.MODID)
public class Hitori {
    public static final String MODID = "hitori";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Hitori() {
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
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HitoriConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, HitoriConfig.CLIENT_SPEC);
        ModNetwork.register();
        FixtureEvents.LISTENER = GameFixtureListener.INSTANCE;
        LOGGER.info("[hitori] ひとりかくれんぼ: the first oni is me.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
