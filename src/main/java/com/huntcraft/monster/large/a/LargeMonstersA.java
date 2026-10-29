package com.huntcraft.monster.large.a;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.registry.HCCreativeTabs;
import net.minecraft.sounds.SoundEvent;
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

/**
 * Module entry point: Frostgarm (氷牙獣フロストガルム, ice fanged beast) + Voltrix (雷鱗竜ヴォルトリクス, thunder leviathan).
 * Called from the HuntCraft constructor.
 */
public final class LargeMonstersA {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HuntCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HuntCraft.MODID);

    public static final RegistryObject<EntityType<FrostgarmEntity>> FROSTGARM = ENTITIES.register("frostgarm",
            () -> EntityType.Builder.of(FrostgarmEntity::new, MobCategory.MONSTER).sized(3.2F, 3.3F)
                    .clientTrackingRange(12).updateInterval(2).build("frostgarm"));
    public static final RegistryObject<EntityType<VoltrixEntity>> VOLTRIX = ENTITIES.register("voltrix",
            () -> EntityType.Builder.of(VoltrixEntity::new, MobCategory.MONSTER).sized(2.8F, 2.5F)
                    .clientTrackingRange(12).updateInterval(2).build("voltrix"));

    public static final RegistryObject<Item> FROSTGARM_EGG = ITEMS.register("frostgarm_spawn_egg",
            () -> new ForgeSpawnEggItem(FROSTGARM, 0xA6CBEA, 0x4FA9D3, new Item.Properties()));
    public static final RegistryObject<Item> VOLTRIX_EGG = ITEMS.register("voltrix_spawn_egg",
            () -> new ForgeSpawnEggItem(VOLTRIX, 0x173149, 0xF2D74A, new Item.Properties()));

    // species sounds (src/main/sounds/monster_large_a.json maps them to vanilla sound files)
    public static final RegistryObject<SoundEvent> FROSTGARM_ROAR = sound("frostgarm.roar");
    public static final RegistryObject<SoundEvent> FROSTGARM_HOWL = sound("frostgarm.howl");
    public static final RegistryObject<SoundEvent> FROSTGARM_GROWL = sound("frostgarm.growl");
    public static final RegistryObject<SoundEvent> FROSTGARM_CRYSTAL = sound("frostgarm.crystal");
    public static final RegistryObject<SoundEvent> VOLTRIX_ROAR = sound("voltrix.roar");
    public static final RegistryObject<SoundEvent> VOLTRIX_GROWL = sound("voltrix.growl");
    public static final RegistryObject<SoundEvent> VOLTRIX_CHARGE = sound("voltrix.charge");
    public static final RegistryObject<SoundEvent> VOLTRIX_ZAP = sound("voltrix.zap");

    private LargeMonstersA() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HuntCraft.id(name)));
    }

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(LargeMonstersA::attributes);
        modBus.addListener(LargeMonstersA::tabs);
        MonsterRegistry.register("frostgarm", FROSTGARM, FrostgarmEntity.PROFILE);
        MonsterRegistry.register("voltrix", VOLTRIX, VoltrixEntity.PROFILE);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(FROSTGARM.get(), HuntMonster.baseAttributes(650, 0.30, 0.95).build());
        event.put(VOLTRIX.get(), HuntMonster.baseAttributes(620, 0.27, 0.95).build());
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HCCreativeTabs.MONSTERS.getKey())) {
            event.accept(FROSTGARM_EGG);
            event.accept(VOLTRIX_EGG);
        }
    }
}
