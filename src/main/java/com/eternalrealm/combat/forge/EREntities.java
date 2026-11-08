package com.eternalrealm.combat.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity types of the combat system (projectiles; never saved). */
public final class EREntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModInfo.MOD_ID);

    public static final RegistryObject<EntityType<MagicProjectile>> MAGIC_PROJECTILE = ENTITIES.register("magic_projectile",
            () -> EntityType.Builder.<MagicProjectile>of(MagicProjectile::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(8).updateInterval(1).noSave()
                    .build(ModInfo.MOD_ID + ":magic_projectile"));
    public static final RegistryObject<EntityType<SkillArrow>> SKILL_ARROW = ENTITIES.register("skill_arrow",
            () -> EntityType.Builder.<SkillArrow>of(SkillArrow::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20).noSave()
                    .build(ModInfo.MOD_ID + ":skill_arrow"));

    private EREntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
