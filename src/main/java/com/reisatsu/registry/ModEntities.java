package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Spirits, the medium and her car (owner: game). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Reisatsu.MODID);

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
    }
}
