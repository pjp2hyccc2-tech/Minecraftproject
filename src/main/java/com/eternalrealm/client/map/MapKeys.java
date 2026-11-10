package com.eternalrealm.client.map;

import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.ui.map.MinimapOverlay;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Keys of the map (Options → Controls → ETERNAL REALM, same category as the other keys) and the registration of the
 * minimap HUD. Kept apart from {@code ClientKeyMappings}/{@code ClientModEvents} so the map stays one module.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MapKeys {
    /** Opens (and closes) the world map. */
    public static final KeyMapping WORLD_MAP = key("world_map", GLFW.GLFW_KEY_M);
    /** Shows or hides the minimap. */
    public static final KeyMapping MINIMAP_TOGGLE = key("minimap_toggle", GLFW.GLFW_KEY_Y);
    public static final KeyMapping MINIMAP_ZOOM_IN = key("minimap_zoom_in", GLFW.GLFW_KEY_EQUAL);
    public static final KeyMapping MINIMAP_ZOOM_OUT = key("minimap_zoom_out", GLFW.GLFW_KEY_MINUS);

    private MapKeys() {
    }

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping("key." + ModInfo.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, defaultKey, ClientKeyMappings.CATEGORY);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(WORLD_MAP);
        event.register(MINIMAP_TOGGLE);
        event.register(MINIMAP_ZOOM_IN);
        event.register(MINIMAP_ZOOM_OUT);
    }

    @SubscribeEvent
    public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.POTION_ICONS.id(), "minimap", new MinimapOverlay());
    }
}
