package com.sounan.registry;

import com.sounan.Sounan;
import com.sounan.entity.Koyanushi;
import com.sounan.entity.Nozoki;
import com.sounan.entity.Oyamasama;
import com.sounan.entity.Ushiro;
import com.sounan.entity.Utsurumono;
import com.sounan.entity.Yobigoe;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Sounan.MODID);

    public static final RegistryObject<EntityType<Nozoki>> NOZOKI = ENTITIES.register("nozoki",
            () -> EntityType.Builder.<Nozoki>of(Nozoki::new, MobCategory.MONSTER)
                    .sized(0.6f, 2.6f).fireImmune().clientTrackingRange(10).build(Sounan.MODID + ":nozoki"));
    public static final RegistryObject<EntityType<Yobigoe>> YOBIGOE = ENTITIES.register("yobigoe",
            () -> EntityType.Builder.<Yobigoe>of(Yobigoe::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.9f).fireImmune().clientTrackingRange(16).build(Sounan.MODID + ":yobigoe"));
    public static final RegistryObject<EntityType<Ushiro>> USHIRO = ENTITIES.register("ushiro",
            () -> EntityType.Builder.<Ushiro>of(Ushiro::new, MobCategory.MONSTER)
                    .sized(0.6f, 2.2f).fireImmune().clientTrackingRange(8).build(Sounan.MODID + ":ushiro"));
    public static final RegistryObject<EntityType<Koyanushi>> KOYANUSHI = ENTITIES.register("koyanushi",
            () -> EntityType.Builder.<Koyanushi>of(Koyanushi::new, MobCategory.MONSTER)
                    .sized(0.7f, 1.8f).fireImmune().clientTrackingRange(10).build(Sounan.MODID + ":koyanushi"));
    public static final RegistryObject<EntityType<Oyamasama>> OYAMASAMA = ENTITIES.register("oyamasama",
            () -> EntityType.Builder.<Oyamasama>of(Oyamasama::new, MobCategory.MONSTER)
                    .sized(1.2f, 4.2f).fireImmune().clientTrackingRange(16).build(Sounan.MODID + ":oyamasama"));
    public static final RegistryObject<EntityType<Utsurumono>> UTSURUMONO = ENTITIES.register("utsurumono",
            () -> EntityType.Builder.<Utsurumono>of(Utsurumono::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).fireImmune().clientTrackingRange(10).build(Sounan.MODID + ":utsurumono"));

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(NOZOKI.get(), Nozoki.createAttributes().build());
        event.put(YOBIGOE.get(), Yobigoe.createAttributes().build());
        event.put(USHIRO.get(), Ushiro.createAttributes().build());
        event.put(KOYANUSHI.get(), Koyanushi.createAttributes().build());
        event.put(OYAMASAMA.get(), Oyamasama.createAttributes().build());
        event.put(UTSURUMONO.get(), Utsurumono.createAttributes().build());
    }
}
