package com.sounan.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sounan.Sounan;
import com.sounan.client.render.PlaceholderRenderer;
import com.sounan.registry.ModEntities;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Sounan.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    public static final KeyMapping PHONE_KEY = new KeyMapping("key.sounan.phone", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P,
            "key.categories.sounan");
    public static final KeyMapping MEMO_KEY = new KeyMapping("key.sounan.memo", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J,
            "key.categories.sounan");

    private ClientSetup() {}

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(ShaderPackInstaller::install);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NOZOKI.get(), c -> new PlaceholderRenderer<>(c, "minecraft:textures/entity/zombie/drowned.png", 1.35f));
        event.registerEntityRenderer(ModEntities.YOBIGOE.get(), c -> new PlaceholderRenderer<>(c, "minecraft:textures/entity/zombie/husk.png", 1.0f));
        event.registerEntityRenderer(ModEntities.USHIRO.get(), c -> new PlaceholderRenderer<>(c, "minecraft:textures/entity/zombie/zombie.png", 1.15f));
        event.registerEntityRenderer(ModEntities.KOYANUSHI.get(), c -> new PlaceholderRenderer<>(c, "minecraft:textures/entity/zombie/husk.png", 0.95f));
        event.registerEntityRenderer(ModEntities.OYAMASAMA.get(), c -> new PlaceholderRenderer<>(c, "minecraft:textures/entity/zombie/drowned.png", 2.2f));
        event.registerEntityRenderer(ModEntities.UTSURUMONO.get(), c -> new PlaceholderRenderer<>(c, "minecraft:textures/entity/zombie/zombie.png", 0.95f));
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("sounan_world", HorrorOverlay::renderWorld);
        event.registerAboveAll("sounan_hud", HorrorOverlay::renderHud);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(PHONE_KEY);
        event.register(MEMO_KEY);
    }

    @SubscribeEvent
    public static void dimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Sounan.id("kaerazu"), new MountainEffects());
    }
}
