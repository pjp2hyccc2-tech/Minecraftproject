package com.clockworkdiver.registry;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.customer.CustomerEntity;
import com.clockworkdiver.entity.BoltProjectile;
import com.clockworkdiver.entity.CogGremlin;
import com.clockworkdiver.entity.DustMoth;
import com.clockworkdiver.entity.RustMite;
import com.clockworkdiver.entity.TimeEater;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ClockworkDiver.MODID);

    public static final RegistryObject<EntityType<CustomerEntity>> CUSTOMER = ENTITIES.register("customer",
            () -> EntityType.Builder.<CustomerEntity>of(CustomerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).build(ClockworkDiver.MODID + ":customer"));
    public static final RegistryObject<EntityType<RustMite>> RUST_MITE = ENTITIES.register("rust_mite",
            () -> EntityType.Builder.<RustMite>of(RustMite::new, MobCategory.MONSTER)
                    .sized(0.9f, 0.55f).clientTrackingRange(8).build(ClockworkDiver.MODID + ":rust_mite"));
    public static final RegistryObject<EntityType<CogGremlin>> COG_GREMLIN = ENTITIES.register("cog_gremlin",
            () -> EntityType.Builder.<CogGremlin>of(CogGremlin::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.2f).clientTrackingRange(8).build(ClockworkDiver.MODID + ":cog_gremlin"));
    public static final RegistryObject<EntityType<DustMoth>> DUST_MOTH = ENTITIES.register("dust_moth",
            () -> EntityType.Builder.<DustMoth>of(DustMoth::new, MobCategory.MONSTER)
                    .sized(0.9f, 0.6f).clientTrackingRange(8).build(ClockworkDiver.MODID + ":dust_moth"));
    public static final RegistryObject<EntityType<TimeEater>> TIME_EATER = ENTITIES.register("time_eater",
            () -> EntityType.Builder.<TimeEater>of(TimeEater::new, MobCategory.MONSTER)
                    .sized(2.2f, 2.4f).fireImmune().clientTrackingRange(10).build(ClockworkDiver.MODID + ":time_eater"));
    public static final RegistryObject<EntityType<BoltProjectile>> BOLT = ENTITIES.register("bolt",
            () -> EntityType.Builder.<BoltProjectile>of(BoltProjectile::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(6).updateInterval(10).build(ClockworkDiver.MODID + ":bolt"));

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CUSTOMER.get(), CustomerEntity.createAttributes().build());
        event.put(RUST_MITE.get(), RustMite.createAttributes().build());
        event.put(COG_GREMLIN.get(), CogGremlin.createAttributes().build());
        event.put(DUST_MOTH.get(), DustMoth.createAttributes().build());
        event.put(TIME_EATER.get(), TimeEater.createAttributes().build());
    }
}
