package com.eternalrealm.job.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity types of the job skills: the necromancer finale's skeleton knight-king ({@link BoneKing}; never saved).
 * Wired with one call in the mod constructor; the client renderer is registered in
 * {@code client.entity.EREntityRenderers}.
 */
public final class JobEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ModInfo.MOD_ID);

    public static final RegistryObject<EntityType<BoneKing>> BONE_KING = ENTITY_TYPES.register("bone_king",
            () -> EntityType.Builder.<BoneKing>of(BoneKing::new, MobCategory.MISC)
                    .sized(1.4F, 4.2F).fireImmune().clientTrackingRange(10).updateInterval(2).noSave()
                    .build(ModInfo.MOD_ID + ":bone_king"));

    private JobEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(JobEntities::onAttributeCreation);
        MinecraftForge.EVENT_BUS.addListener(JobEntities::onLoggedOut);
    }

    private static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(BONE_KING.get(), BoneKing.createAttributes().build());
    }

    /** A summoner who leaves takes its marks along (its king crumbles by itself once its summoner is gone). */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        FinaleMechanics.forget(event.getEntity().getUUID());
    }
}
