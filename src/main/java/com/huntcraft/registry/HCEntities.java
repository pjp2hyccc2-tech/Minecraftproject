package com.huntcraft.registry;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.misc.HazardEntity;
import com.huntcraft.entity.misc.HCThrownItem;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.misc.TrapEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Core (shared) entity types. Monsters / NPCs are registered in their own modules. */
public final class HCEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HuntCraft.MODID);

    private HCEntities() {
    }

    public static final RegistryObject<EntityType<TrapEntity>> TRAP = ENTITIES.register("trap",
            () -> EntityType.Builder.<TrapEntity>of(TrapEntity::new, MobCategory.MISC).sized(1.8F, 0.35F)
                    .clientTrackingRange(10).updateInterval(10).build("trap"));

    public static final RegistryObject<EntityType<HCThrownItem>> THROWN_ITEM = ENTITIES.register("thrown_item",
            () -> EntityType.Builder.<HCThrownItem>of(HCThrownItem::new, MobCategory.MISC).sized(0.3F, 0.3F)
                    .clientTrackingRange(6).updateInterval(10).build("thrown_item"));

    /** Generic elemental projectile (fireball, ice shard, thunder orb, water jet, dragon orb, rock...). */
    public static final RegistryObject<EntityType<MonsterProjectile>> MONSTER_PROJECTILE = ENTITIES.register("monster_projectile",
            () -> EntityType.Builder.<MonsterProjectile>of(MonsterProjectile::new, MobCategory.MISC).sized(0.8F, 0.8F)
                    .clientTrackingRange(8).updateInterval(1).fireImmune().build("monster_projectile"));

    /** Lingering / delayed area hazard (fire pool, lightning strike marker, ice field, meteor impact...). */
    public static final RegistryObject<EntityType<HazardEntity>> HAZARD = ENTITIES.register("hazard",
            () -> EntityType.Builder.<HazardEntity>of(HazardEntity::new, MobCategory.MISC).sized(1.0F, 0.5F)
                    .clientTrackingRange(8).updateInterval(5).fireImmune().build("hazard"));
}
