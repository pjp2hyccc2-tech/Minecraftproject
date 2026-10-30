package com.mcbesiege.client;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.registry.ModBlockEntities;
import com.mcbesiege.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client-only registration on the mod bus. */
@Mod.EventBusSubscriber(modid = MCBesiege.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    public static final String CATEGORY = "key.categories.mcbesiege";
    public static final KeyMapping KEY_SIMULATE = new KeyMapping("key.mcbesiege.simulate", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
    public static final KeyMapping KEY_CONTROL = new KeyMapping("key.mcbesiege.control", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping KEY_BUILDER = new KeyMapping("key.mcbesiege.builder", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
    public static final KeyMapping KEY_MIRROR = new KeyMapping("key.mcbesiege.mirror", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.MACHINE.get(), MachineRenderer::new);
        e.registerEntityRenderer(ModEntities.CANNONBALL.get(), ThrownItemRenderer::new);
        e.registerBlockEntityRenderer(ModBlockEntities.MACHINE_PART.get(), PartBER::new);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(KEY_SIMULATE);
        e.register(KEY_CONTROL);
        e.register(KEY_BUILDER);
        e.register(KEY_MIRROR);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("hud", Hud::render);
    }
}
