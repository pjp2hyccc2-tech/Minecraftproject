package com.hitori.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Textures that other workers deliver (art, models): is one there yet? Missing ones get a drawn stand-in instead of the
 * magenta checkerboard. Cleared on resource reload.
 */
public final class Tex {
    private static final Map<ResourceLocation, Boolean> HAS = new ConcurrentHashMap<>();

    private Tex() {}

    public static boolean has(ResourceLocation rl) {
        return HAS.computeIfAbsent(rl, r -> {
            try {
                return Minecraft.getInstance().getResourceManager().getResource(r).isPresent();
            } catch (Exception e) {
                return false;
            }
        });
    }

    public static void clear() {
        HAS.clear();
    }
}
