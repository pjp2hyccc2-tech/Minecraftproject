package jp.satsujinki;

import com.mojang.logging.LogUtils;
import jp.satsujinki.network.ModNetwork;
import jp.satsujinki.registry.ModBlockEntities;
import jp.satsujinki.registry.ModBlocks;
import jp.satsujinki.registry.ModCreativeTabs;
import jp.satsujinki.registry.ModEntities;
import jp.satsujinki.registry.ModItems;
import jp.satsujinki.registry.ModParticles;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 殺人喜 -SATSUJINKI- : an R18 death-game mod. The player is the host of a death game and executes
 * contestants (NPCs with a fear-driven AI) with giant mixers, spike presses, saws and guillotines.
 */
@Mod(Satsujinki.MODID)
public class Satsujinki {
    public static final String MODID = "satsujinki";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Satsujinki() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModParticles.PARTICLES.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(ModEntities::onAttributes);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SatsujinkiConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SatsujinkiConfig.CLIENT_SPEC);
        ModNetwork.register();
        LOGGER.info("殺人喜 -SATSUJINKI- loaded. R18: violence and gore ahead.");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
