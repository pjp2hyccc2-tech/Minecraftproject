package com.mcbesiege.registry;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.entity.CannonballEntity;
import com.mcbesiege.entity.MachineEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MCBesiege.MODID);

    public static final RegistryObject<EntityType<MachineEntity>> MACHINE = ENTITIES.register("machine",
            () -> EntityType.Builder.<MachineEntity>of(MachineEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).clientTrackingRange(12).updateInterval(1).fireImmune().noSummon()
                    .build(MCBesiege.MODID + ":machine"));

    public static final RegistryObject<EntityType<CannonballEntity>> CANNONBALL = ENTITIES.register("cannonball",
            () -> EntityType.Builder.<CannonballEntity>of(CannonballEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).fireImmune().noSummon()
                    .build(MCBesiege.MODID + ":cannonball"));
}
