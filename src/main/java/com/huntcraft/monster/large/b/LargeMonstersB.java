package com.huntcraft.monster.large.b;

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

/** Module entry point: Gravadon (岩槌竜) + Azragos (冥炎龍). Called from the HuntCraft constructor. */
public final class LargeMonstersB {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HuntCraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HuntCraft.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HuntCraft.MODID);

    public static final RegistryObject<EntityType<GravadonEntity>> GRAVADON = ENTITIES.register("gravadon",
            () -> EntityType.Builder.of(GravadonEntity::new, MobCategory.MONSTER).sized(2.8F, 3.4F)
                    .clientTrackingRange(12).updateInterval(2).build("gravadon"));
    public static final RegistryObject<EntityType<AzragosEntity>> AZRAGOS = ENTITIES.register("azragos",
            () -> EntityType.Builder.of(AzragosEntity::new, MobCategory.MONSTER).sized(3.0F, 3.4F)
                    .clientTrackingRange(14).updateInterval(2).fireImmune().build("azragos"));

    public static final RegistryObject<Item> GRAVADON_EGG = ITEMS.register("gravadon_spawn_egg",
            () -> new ForgeSpawnEggItem(GRAVADON, 0x4E3F30, 0x5FD9CC, new Item.Properties()));
    public static final RegistryObject<Item> AZRAGOS_EGG = ITEMS.register("azragos_spawn_egg",
            () -> new ForgeSpawnEggItem(AZRAGOS, 0x231434, 0xE9409C, new Item.Properties()));

    public static final RegistryObject<SoundEvent> GRAVADON_ROAR = sound("monster.gravadon_roar");
    public static final RegistryObject<SoundEvent> GRAVADON_SLAM = sound("monster.gravadon_slam");
    public static final RegistryObject<SoundEvent> AZRAGOS_ROAR = sound("monster.azragos_roar");
    public static final RegistryObject<SoundEvent> AZRAGOS_CHARGE = sound("monster.azragos_charge");
    public static final RegistryObject<SoundEvent> AZRAGOS_NOVA = sound("monster.azragos_nova");

    private LargeMonstersB() {
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(HuntCraft.id(name)));
    }

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(LargeMonstersB::attributes);
        modBus.addListener(LargeMonstersB::tabs);
        MonsterRegistry.register("gravadon", GRAVADON, GravadonEntity.PROFILE);
        MonsterRegistry.register("azragos", AZRAGOS, AzragosEntity.PROFILE);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(GRAVADON.get(), HuntMonster.baseAttributes(800, 0.22, 1.0).build());
        event.put(AZRAGOS.get(), HuntMonster.baseAttributes(1000, 0.25, 1.0).build());
    }

    private static void tabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(HCCreativeTabs.MONSTERS.getKey())) {
            event.accept(GRAVADON_EGG);
            event.accept(AZRAGOS_EGG);
        }
    }
}
