package com.huntcraft.monster.ignivald;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.registry.HCCreativeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Module entry point: Ignivald (core reference large monster). */
public final class IgnivaldModule {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HuntCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);

    public static final RegistryObject<EntityType<IgnivaldEntity>> IGNIVALD = ENTITIES.register("ignivald",
            () -> EntityType.Builder.of(IgnivaldEntity::new, MobCategory.MONSTER).sized(3.2F, 3.0F)
                    .clientTrackingRange(12).updateInterval(2).fireImmune().build("ignivald"));

    public static final RegistryObject<Item> IGNIVALD_EGG = ITEMS.register("ignivald_spawn_egg",
            () -> new ForgeSpawnEggItem(IGNIVALD, 0x8A1D0C, 0xF59A48, new Item.Properties()));

    private IgnivaldModule() {
    }

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(IgnivaldModule::attributes);
        modBus.addListener(IgnivaldModule::tabs);
        MonsterRegistry.register("ignivald", IGNIVALD, IgnivaldEntity.PROFILE);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(IGNIVALD.get(), HuntMonster.baseAttributes(700, 0.27, 0.95).build());
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HCCreativeTabs.MONSTERS.getKey())) event.accept(IGNIVALD_EGG);
    }

}
