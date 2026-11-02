package com.clockworkdiver.client;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.client.hud.ClockworkHud;
import com.clockworkdiver.client.render.CogGremlinModel;
import com.clockworkdiver.client.render.CogGremlinRenderer;
import com.clockworkdiver.client.render.CustomerRenderer;
import com.clockworkdiver.client.render.DustMothModel;
import com.clockworkdiver.client.render.DustMothRenderer;
import com.clockworkdiver.client.render.MechanismRenderer;
import com.clockworkdiver.client.render.RustMiteModel;
import com.clockworkdiver.client.render.RustMiteRenderer;
import com.clockworkdiver.client.render.TimeEaterModel;
import com.clockworkdiver.client.render.TimeEaterRenderer;
import com.clockworkdiver.client.render.TopHatModel;
import com.clockworkdiver.item.BrokenClockItem;
import com.clockworkdiver.item.KeepsakeItem;
import com.clockworkdiver.registry.ModBlockEntities;
import com.clockworkdiver.registry.ModEntities;
import com.clockworkdiver.registry.ModItems;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ClockworkDiver.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping ABORT_KEY = new KeyMapping("key.clockworkdiver.abort", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
            "key.categories.clockworkdiver");
    public static final KeyMapping JOURNAL_KEY = new KeyMapping("key.clockworkdiver.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.clockworkdiver");

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ModItems.BROKEN_CLOCK.get(), ClockworkDiver.id("clock_type"),
                    (stack, level, entity, seed) -> BrokenClockItem.typeIndex(stack) / 10.0f);
            ItemProperties.register(ModItems.KEEPSAKE.get(), ClockworkDiver.id("keepsake"),
                    (stack, level, entity, seed) -> KeepsakeItem.icon(stack));
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MECHANISM.get(), MechanismRenderer::new);
        event.registerEntityRenderer(ModEntities.CUSTOMER.get(), CustomerRenderer::new);
        event.registerEntityRenderer(ModEntities.RUST_MITE.get(), RustMiteRenderer::new);
        event.registerEntityRenderer(ModEntities.COG_GREMLIN.get(), CogGremlinRenderer::new);
        event.registerEntityRenderer(ModEntities.DUST_MOTH.get(), DustMothRenderer::new);
        event.registerEntityRenderer(ModEntities.TIME_EATER.get(), TimeEaterRenderer::new);
        event.registerEntityRenderer(ModEntities.BOLT.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(RustMiteModel.LAYER, RustMiteModel::createLayer);
        event.registerLayerDefinition(CogGremlinModel.LAYER, CogGremlinModel::createLayer);
        event.registerLayerDefinition(DustMothModel.LAYER, DustMothModel::createLayer);
        event.registerLayerDefinition(TimeEaterModel.LAYER, TimeEaterModel::createLayer);
        event.registerLayerDefinition(TopHatModel.LAYER, TopHatModel::createLayer);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hud", ClockworkHud.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ABORT_KEY);
        event.register(JOURNAL_KEY);
    }
}
