package com.meikon;

import com.meikon.network.ModNetwork;
import com.meikon.registry.ModBlocks;
import com.meikon.registry.ModCreativeTab;
import com.meikon.registry.ModEntities;
import com.meikon.registry.ModItems;
import com.meikon.registry.ModPaintings;
import com.meikon.registry.ModSounds;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 冥婚 ―MEIKON―: a Japanese folk-horror game. You opened a red envelope and were invited to a ghost wedding.
 * Gather the seven bells in the mansion of eternal night and sever the bond at the torii before the bride finds you.
 */
@Mod(Meikon.MODID)
public class Meikon {
    public static final String MODID = "meikon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Meikon() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModSounds.SOUNDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModPaintings.PAINTINGS.register(modBus);
        ModCreativeTab.TABS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MeikonConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, MeikonConfig.CLIENT_SPEC);
        ModNetwork.register();
        LOGGER.info("[meikon] 冥婚: the invitation has been sent");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
