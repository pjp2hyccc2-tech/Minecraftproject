package com.meikon.registry;

import com.meikon.Meikon;
import com.meikon.entity.Hanayome;
import com.meikon.entity.Ichimatsu;
import com.meikon.entity.Kageboshi;
import com.meikon.entity.Maboroshi;
import com.meikon.entity.Ohajiki;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Meikon.MODID);

    public static final RegistryObject<EntityType<Hanayome>> HANAYOME = ENTITIES.register("hanayome",
            () -> EntityType.Builder.<Hanayome>of(Hanayome::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.85f).fireImmune().clientTrackingRange(12).build(Meikon.MODID + ":hanayome"));
    public static final RegistryObject<EntityType<Ichimatsu>> ICHIMATSU = ENTITIES.register("ichimatsu",
            () -> EntityType.Builder.<Ichimatsu>of(Ichimatsu::new, MobCategory.MONSTER)
                    .sized(0.45f, 0.9f).fireImmune().clientTrackingRange(10).build(Meikon.MODID + ":ichimatsu"));
    public static final RegistryObject<EntityType<Kageboshi>> KAGEBOSHI = ENTITIES.register("kageboshi",
            () -> EntityType.Builder.<Kageboshi>of(Kageboshi::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.9f).fireImmune().clientTrackingRange(10).build(Meikon.MODID + ":kageboshi"));
    public static final RegistryObject<EntityType<Maboroshi>> MABOROSHI = ENTITIES.register("maboroshi",
            () -> EntityType.Builder.<Maboroshi>of(Maboroshi::new, MobCategory.MISC)
                    .sized(0.6f, 1.85f).fireImmune().clientTrackingRange(12).build(Meikon.MODID + ":maboroshi"));
    public static final RegistryObject<EntityType<Ohajiki>> OHAJIKI = ENTITIES.register("ohajiki",
            () -> EntityType.Builder.<Ohajiki>of(Ohajiki::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(6).updateInterval(10).build(Meikon.MODID + ":ohajiki"));

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(HANAYOME.get(), Hanayome.createAttributes().build());
        event.put(ICHIMATSU.get(), Ichimatsu.createAttributes().build());
        event.put(KAGEBOSHI.get(), Kageboshi.createAttributes().build());
        event.put(MABOROSHI.get(), Maboroshi.createAttributes().build());
    }
}
