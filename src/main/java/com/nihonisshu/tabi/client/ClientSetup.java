package com.nihonisshu.tabi.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.registry.TabiBlockEntities;
import com.nihonisshu.tabi.registry.TabiEntities;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Client-only registration on the mod bus. */
@Mod.EventBusSubscriber(modid = TabiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    private ClientSetup() {}

    public static final KeyMapping KEY_JOURNAL = new KeyMapping("key.tabi.journal", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.tabi");
    public static final KeyMapping KEY_GRADE = new KeyMapping("key.tabi.grade", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.tabi");

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(TabiEntities.NPC.get(), NpcRenderer::new);
        e.registerEntityRenderer(TabiEntities.VEHICLE.get(), VehicleRenderer::new);
        e.registerEntityRenderer(TabiEntities.SEAT.get(), NoopRenderer::new);
        e.registerBlockEntityRenderer(TabiBlockEntities.TEXT_PANEL.get(), TextPanelRenderer::new);
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent e) {
        e.register(KEY_JOURNAL);
        e.register(KEY_GRADE);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("hud", Hud::render);
    }

    @SubscribeEvent
    public static void shaders(RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), new ResourceLocation(TabiMod.MODID, "grade"), DefaultVertexFormat.POSITION_TEX),
                    s -> Grading.shader = s);
        } catch (Exception ex) {
            TabiMod.LOGGER.error("[tabi] colour grading shader failed to load; grading disabled", ex);
            Grading.shader = null;
        }
    }
}
