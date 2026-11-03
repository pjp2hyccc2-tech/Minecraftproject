package com.hitori.registry;

import com.hitori.Hitori;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** The doll, the woman, the cameras (owner: game). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Hitori.MODID);

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
    }
}
