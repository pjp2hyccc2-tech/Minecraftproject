package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.entity.CorpseEntity;
import jp.satsujinki.entity.GibEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Satsujinki.MODID);

    public static final RegistryObject<EntityType<ContestantEntity>> CONTESTANT = ENTITIES.register("contestant",
            () -> EntityType.Builder.<ContestantEntity>of(ContestantEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).updateInterval(2)
                    .build(Satsujinki.id("contestant").toString()));
    public static final RegistryObject<EntityType<CorpseEntity>> CORPSE = ENTITIES.register("corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.35F).clientTrackingRange(8).updateInterval(3)
                    .build(Satsujinki.id("corpse").toString()));
    public static final RegistryObject<EntityType<GibEntity>> GIB = ENTITIES.register("gib",
            () -> EntityType.Builder.<GibEntity>of(GibEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(6).updateInterval(2)
                    .build(Satsujinki.id("gib").toString()));

    private ModEntities() {
    }

    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(CONTESTANT.get(), ContestantEntity.createAttributes().build());
    }
}
