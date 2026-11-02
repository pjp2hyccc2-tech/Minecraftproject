package com.birdiecraft.registry;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.entity.CpuGolferEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, BirdieCraft.MODID);

    public static final RegistryObject<EntityType<CpuGolferEntity>> CPU_GOLFER = ENTITIES.register("cpu_golfer",
            () -> EntityType.Builder.<CpuGolferEntity>of(CpuGolferEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build(BirdieCraft.MODID + ":cpu_golfer"));

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CPU_GOLFER.get(), CpuGolferEntity.createAttributes().build());
    }

    private ModEntities() {
    }
}
