package com.huntcraft.village;

import com.huntcraft.core.HuntHooks;
import com.huntcraft.registry.HCCreativeTabs;
import com.huntcraft.village.companion.MofuEntity;
import com.huntcraft.village.meal.MealSystem;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import com.huntcraft.village.world.VillageTravel;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Module entry point: Kazami village hub dimension, NPCs, GUIs (blacksmith, merchant, kitchen, companions, hunter notes,
 * item box) and the mofu companion system. Called from the HuntCraft constructor.
 */
public final class VillageModule {
    private VillageModule() {
    }

    public static void init(IEventBus modBus) {
        VillageRegistry.BLOCKS.register(modBus);
        VillageRegistry.ITEMS.register(modBus);
        VillageRegistry.ENTITIES.register(modBus);
        VillageRegistry.MENUS.register(modBus);
        VillageRegistry.SOUNDS.register(modBus);
        modBus.addListener(VillageModule::onAttributes);
        modBus.addListener(VillageModule::onCreativeTabs);

        HuntHooks.toVillage = VillageTravel::toVillage;
        HuntHooks.toOverworld = VillageTravel::toOverworld;
        HuntHooks.addExtraCarveProvider(MealSystem::extraCarves);
        HuntHooks.addAttackMultiplier(MealSystem::attackMultiplier);
        VillageActions.register();
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(VillageRegistry.HUMAN_NPC.get(), VillageNpc.createAttributes().build());
        event.put(VillageRegistry.MOFU_NPC.get(), VillageNpc.createAttributes().build());
        event.put(VillageRegistry.MOFU.get(), MofuEntity.createAttributes().build());
        event.put(VillageRegistry.TRAINING_DUMMY.get(), TrainingDummy.createAttributes().build());
    }

    private static void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HCCreativeTabs.ITEMS.getKey())) {
            event.accept(VillageRegistry.ITEM_BOX_ITEM.get());
            event.accept(VillageRegistry.QUEST_BOARD_ITEM.get());
            event.accept(VillageRegistry.PAPER_LANTERN_ITEM.get());
            event.accept(VillageRegistry.PAPER_LANTERN_WHITE_ITEM.get());
        }
    }
}
