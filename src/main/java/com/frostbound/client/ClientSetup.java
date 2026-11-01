package com.frostbound.client;

import com.frostbound.FrostboundMod;
import com.frostbound.registry.ModRegistries;
import com.frostbound.world.Dims;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client registrations on the mod bus: key, HUD overlay, sky/fog effects of the dimension, citizen renderer. */
@Mod.EventBusSubscriber(modid = FrostboundMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping MANAGEMENT_KEY = new KeyMapping("key.frostbound.management", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M, "key.categories.frostbound");

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(MANAGEMENT_KEY);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hud", HudOverlay.INSTANCE);
    }

    @SubscribeEvent
    public static void onEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Dims.EFFECTS, new FrozenEffects());
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModRegistries.CITIZEN.get(), CitizenRenderer::new);
    }
}
