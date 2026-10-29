package com.huntcraft.quest;

import com.huntcraft.core.HuntHooks;
import com.huntcraft.registry.HCCreativeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;

/**
 * Module entry point: quests, quest maps, gathering, camps. Called from the HuntCraft constructor.
 * <p>Integration: the village calls {@link QuestBoardService#open(net.minecraft.server.level.ServerPlayer)} (or just opens
 * HCScreens.QUEST_BOARD; the screen requests its data itself). This module installs HuntHooks.toBaseCamp / questRank.
 */
public final class QuestModule {
    private QuestModule() {
    }

    public static void init(IEventBus modBus) {
        QuestRegistry.BLOCKS.register(modBus);
        QuestRegistry.ITEMS.register(modBus);
        QuestRegistry.CHUNK_GENERATORS.register(modBus);
        QuestRegistry.BIOME_SOURCES.register(modBus);
        modBus.addListener(QuestModule::tabs);
        QuestBoardService.registerActions();
        HuntHooks.toBaseCamp = QuestManager::toBaseCamp;
        HuntHooks.questRank = QuestManager::rankOf;
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HCCreativeTabs.ITEMS.getKey())) {
            for (RegistryObject<net.minecraft.world.item.Item> ro : QuestRegistry.TAB_ITEMS) event.accept(ro);
        }
    }
}
