package com.reisatsu.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Textures that other workers deliver (art, spirits): is one there yet? Missing ones get a drawn stand-in instead of
 * the magenta checkerboard. Cleared on resource reload.
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

    /** Reads a texture's pixels (ABGR) from the resources, or null. Safe off the render thread. */
    @Nullable
    public static int[] pixels(ResourceLocation rl, int[] size) {
        try {
            Optional<Resource> r = Minecraft.getInstance().getResourceManager().getResource(rl);
            if (r.isEmpty()) return null;
            try (InputStream in = r.get().open(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth(), h = img.getHeight();
                int[] px = new int[w * h];
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = img.getPixelRGBA(x, y);
                size[0] = w;
                size[1] = h;
                return px;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
