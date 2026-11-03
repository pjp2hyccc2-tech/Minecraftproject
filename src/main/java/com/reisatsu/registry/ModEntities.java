package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import com.reisatsu.entity.Chef;
import com.reisatsu.entity.Child;
import com.reisatsu.entity.Manager;
import com.reisatsu.entity.Medium;
import com.reisatsu.entity.MediumCar;
import com.reisatsu.entity.MirrorWoman;
import com.reisatsu.entity.Spirit;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.entity.Visitor;
import com.reisatsu.entity.Waitress;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Spirits, the medium and her car (owner: game). */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Reisatsu.MODID);

    public static final RegistryObject<EntityType<Visitor>> VISITOR = ENTITIES.register("visitor",
            () -> EntityType.Builder.<Visitor>of(Visitor::new, MobCategory.MISC).sized(0.6f, 1.9f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Reisatsu.MODID + ":visitor"));
    public static final RegistryObject<EntityType<Waitress>> WAITRESS = ENTITIES.register("waitress",
            () -> EntityType.Builder.<Waitress>of(Waitress::new, MobCategory.MISC).sized(0.6f, 1.8f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Reisatsu.MODID + ":waitress"));
    public static final RegistryObject<EntityType<Chef>> CHEF = ENTITIES.register("chef",
            () -> EntityType.Builder.<Chef>of(Chef::new, MobCategory.MISC).sized(0.8f, 2.2f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Reisatsu.MODID + ":chef"));
    public static final RegistryObject<EntityType<Child>> CHILD = ENTITIES.register("child",
            () -> EntityType.Builder.<Child>of(Child::new, MobCategory.MISC).sized(0.45f, 1.1f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Reisatsu.MODID + ":child"));
    public static final RegistryObject<EntityType<MirrorWoman>> MIRROR_WOMAN = ENTITIES.register("mirror_woman",
            () -> EntityType.Builder.<MirrorWoman>of(MirrorWoman::new, MobCategory.MISC).sized(0.6f, 1.8f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Reisatsu.MODID + ":mirror_woman"));
    public static final RegistryObject<EntityType<Manager>> MANAGER = ENTITIES.register("manager",
            () -> EntityType.Builder.<Manager>of(Manager::new, MobCategory.MISC).sized(0.6f, 1.85f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Reisatsu.MODID + ":manager"));
    public static final RegistryObject<EntityType<Medium>> MEDIUM = ENTITIES.register("medium",
            () -> EntityType.Builder.<Medium>of(Medium::new, MobCategory.MISC).sized(0.6f, 1.6f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Reisatsu.MODID + ":medium"));
    public static final RegistryObject<EntityType<MediumCar>> MEDIUM_CAR = ENTITIES.register("medium_car",
            () -> EntityType.Builder.<MediumCar>of(MediumCar::new, MobCategory.MISC).sized(1.6f, 1.5f).fireImmune()
                    .clientTrackingRange(10).updateInterval(20).build(Reisatsu.MODID + ":medium_car"));

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(VISITOR.get(), Spirit.createAttributes().build());
        event.put(WAITRESS.get(), Spirit.createAttributes().build());
        event.put(CHEF.get(), Spirit.createAttributes().build());
        event.put(CHILD.get(), Spirit.createAttributes().build());
        event.put(MIRROR_WOMAN.get(), Spirit.createAttributes().build());
        event.put(MANAGER.get(), Spirit.createAttributes().build());
        event.put(MEDIUM.get(), Medium.createAttributes().build());
    }

    public static EntityType<? extends Spirit> type(SpiritKind kind) {
        return switch (kind) {
            case VISITOR -> VISITOR.get();
            case WAITRESS -> WAITRESS.get();
            case CHEF -> CHEF.get();
            case CHILD -> CHILD.get();
            case MIRROR_WOMAN -> MIRROR_WOMAN.get();
            case MANAGER -> MANAGER.get();
        };
    }

    public static boolean ours(Entity e) {
        return e instanceof Spirit || e instanceof Medium || e instanceof MediumCar;
    }
}
