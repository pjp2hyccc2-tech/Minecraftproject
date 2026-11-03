package com.hitori.registry;

import com.hitori.Hitori;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.OniDoll;
import com.hitori.entity.Woman;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The doll, the woman, the cameras (owner: game). docs/DESIGN.md §5. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Hitori.MODID);

    public static final RegistryObject<EntityType<OniDoll>> ONI_DOLL = ENTITIES.register("oni_doll",
            () -> EntityType.Builder.<OniDoll>of(OniDoll::new, MobCategory.MISC).sized(0.45f, 0.6f).fireImmune()
                    .clientTrackingRange(10).updateInterval(1).build(Hitori.MODID + ":oni_doll"));
    public static final RegistryObject<EntityType<Woman>> WOMAN = ENTITIES.register("woman",
            () -> EntityType.Builder.<Woman>of(Woman::new, MobCategory.MISC).sized(0.6f, 1.8f).fireImmune()
                    .clientTrackingRange(10).updateInterval(2).build(Hitori.MODID + ":woman"));
    public static final RegistryObject<EntityType<FixedCamera>> CAMERA = ENTITIES.register("camera",
            () -> EntityType.Builder.<FixedCamera>of(FixedCamera::new, MobCategory.MISC).sized(0.25f, 0.3f).fireImmune()
                    .clientTrackingRange(16).updateInterval(10).build(Hitori.MODID + ":camera"));

    private ModEntities() {}

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ONI_DOLL.get(), OniDoll.createAttributes().build());
        event.put(WOMAN.get(), Woman.createAttributes().build());
        event.put(CAMERA.get(), FixedCamera.createAttributes().build());
    }

    public static boolean ours(Entity e) {
        return e instanceof OniDoll || e instanceof Woman || e instanceof FixedCamera;
    }
}
