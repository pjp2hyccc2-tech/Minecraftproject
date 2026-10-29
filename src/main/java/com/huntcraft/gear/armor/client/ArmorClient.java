package com.huntcraft.gear.armor.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.gear.armor.ArmorModule;
import com.huntcraft.gear.armor.CharmItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client registration for the armor module (mod bus). */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArmorClient {
    private ArmorClient() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ArmorModule.CHARM.get(), HuntCraft.id("tier"),
                (stack, level, entity, seed) -> CharmItem.getTier(stack) * 0.25F));
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ArmorModels.clearCaches();
        for (String skin : event.getSkins()) {
            addGlow(event.getSkin(skin));
        }
        addGlow(event.getRenderer(EntityType.ARMOR_STAND));
        addGlow(event.getRenderer(EntityType.ZOMBIE));
        addGlow(event.getRenderer(EntityType.SKELETON));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addGlow(LivingEntityRenderer renderer) {
        if (renderer != null && renderer.getModel() instanceof HumanoidModel) {
            renderer.addLayer(new ArmorGlowLayer(renderer));
        }
    }
}
