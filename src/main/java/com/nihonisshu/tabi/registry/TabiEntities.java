package com.nihonisshu.tabi.registry;

import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.entity.SeatEntity;
import com.nihonisshu.tabi.entity.TabiNpc;
import com.nihonisshu.tabi.entity.VehicleEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TabiEntities {
    private TabiEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TabiMod.MODID);

    public static final RegistryObject<EntityType<TabiNpc>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<TabiNpc>of(TabiNpc::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).build(TabiMod.MODID + ":npc"));

    public static final RegistryObject<EntityType<VehicleEntity>> VEHICLE = ENTITIES.register("vehicle",
            () -> EntityType.Builder.<VehicleEntity>of(VehicleEntity::new, MobCategory.MISC)
                    .sized(3.0f, 4.0f).clientTrackingRange(20).updateInterval(1).fireImmune()
                    .build(TabiMod.MODID + ":vehicle"));

    public static final RegistryObject<EntityType<SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<SeatEntity>of(SeatEntity::new, MobCategory.MISC)
                    .sized(0.01f, 0.01f).clientTrackingRange(8).build(TabiMod.MODID + ":seat"));
}
