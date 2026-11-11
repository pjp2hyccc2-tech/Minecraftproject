package com.eternalrealm.mount.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.mount.MountBodies;
import com.eternalrealm.mount.MountKind;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

/**
 * The mount entity types ({@code eternalrealm:mount_horse}, {@code mount_griffin}, {@code mount_bike}): not saved
 * with the world, hitboxes from the models ({@link MountBodies}). Wired with {@code MountEntities.register(modBus)} in
 * the mod constructor; the renderers live in {@code client.mount}.
 */
public final class MountEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModInfo.MOD_ID);
    private static final Map<MountKind, RegistryObject<EntityType<MountEntity>>> TYPES = new EnumMap<>(MountKind.class);

    static {
        for (MountKind kind : MountKind.values()) {
            TYPES.put(kind, ENTITY_TYPES.register(kind.entityPath(),
                    () -> EntityType.Builder.<MountEntity>of((type, level) -> new MountEntity(type, level, kind),
                                    MobCategory.MISC)
                            .sized(MountBodies.width(kind), MountBodies.height(kind)).clientTrackingRange(10)
                            .updateInterval(2).fireImmune().noSave()
                            .build(ModInfo.MOD_ID + ":" + kind.entityPath())));
        }
    }

    private MountEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(MountEntities::onAttributeCreation);
    }

    /** The entity type of a mount (after registration). */
    public static EntityType<MountEntity> type(MountKind kind) {
        return TYPES.get(kind).get();
    }

    private static void onAttributeCreation(EntityAttributeCreationEvent event) {
        for (MountKind kind : MountKind.values()) {
            event.put(type(kind), MountEntity.createAttributes().build());
        }
    }
}
