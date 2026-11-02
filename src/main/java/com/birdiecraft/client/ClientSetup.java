package com.birdiecraft.client;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.client.entity.CpuGolferModel;
import com.birdiecraft.client.entity.CpuGolferRenderer;
import com.birdiecraft.item.GolfClubItem;
import com.birdiecraft.registry.ModEntities;
import com.birdiecraft.registry.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client registration (mod event bus). */
@Mod.EventBusSubscriber(modid = BirdieCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.GOLF_CLUB.get(), BirdieCraft.id("club_type"),
                (stack, level, entity, seed) -> GolfClubItem.getClub(stack).family.modelValue()));
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : Keybinds.ALL) event.register(k);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("golf_hud", new GolfHud());
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CPU_GOLFER.get(), CpuGolferRenderer::new);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CpuGolferModel.LAYER, CpuGolferModel::createLayer);
    }
}
