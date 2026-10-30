package com.nihonisshu.tabi;

import com.mojang.logging.LogUtils;
import com.nihonisshu.tabi.entity.TabiNpc;
import com.nihonisshu.tabi.network.TabiNet;
import com.nihonisshu.tabi.registry.TabiBlockEntities;
import com.nihonisshu.tabi.registry.TabiBlocks;
import com.nihonisshu.tabi.registry.TabiEntities;
import com.nihonisshu.tabi.registry.TabiItems;
import com.nihonisshu.tabi.registry.TabiSounds;
import com.nihonisshu.tabi.registry.TabiTabs;
import com.nihonisshu.tabi.registry.TabiWorldgen;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * 日本一周旅 (Nihon Isshu Tabi): a hand-built Japanese world in its own dimension (tabi:japan) — a farming village,
 * the neighbouring town, a railway line along the Seto Inland Sea and the port city of Kobe.
 */
@Mod(TabiMod.MODID)
public class TabiMod {
    public static final String MODID = "tabi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TabiMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        TabiBlocks.BLOCKS.register(modBus);
        TabiItems.ITEMS.register(modBus);
        TabiBlockEntities.BLOCK_ENTITIES.register(modBus);
        TabiEntities.ENTITIES.register(modBus);
        TabiSounds.SOUNDS.register(modBus);
        TabiTabs.TABS.register(modBus);
        TabiWorldgen.CHUNK_GENERATORS.register(modBus);
        TabiWorldgen.BIOME_SOURCES.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::attributes);
        LOGGER.info("日本一周旅 loading");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TabiNet::init);
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(TabiEntities.NPC.get(), TabiNpc.createAttributes().build());
    }
}
