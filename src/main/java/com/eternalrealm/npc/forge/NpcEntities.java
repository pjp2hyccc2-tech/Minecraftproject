package com.eternalrealm.npc.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The NPC entity type ({@code eternalrealm:npc}) and its attributes. Wired with one call in the mod constructor:
 * {@code NpcEntities.register(modBus)}. The client renderer is registered in {@code client.entity.EREntityRenderers}.
 */
public final class NpcEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModInfo.MOD_ID);

    public static final RegistryObject<EntityType<NpcEntity>> NPC = ENTITY_TYPES.register("npc",
            () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build(ModInfo.MOD_ID + ":npc"));

    private NpcEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(NpcEntities::onAttributeCreation);
    }

    private static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(NPC.get(), NpcEntity.createAttributes().build());
    }
}
