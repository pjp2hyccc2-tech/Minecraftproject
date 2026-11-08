package com.eternalrealm.ui.map;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.map.AtlasPainter;
import com.eternalrealm.world.map.MapMath;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * The world map's atlas pictures ({@link AtlasPainter}), painted once per game on a background thread the first time
 * they are asked for and then uploaded as textures: one with the biomes of アストリア, one with the area rings only
 * (realms without the dedicated dimension). Render thread only (except the painting itself).
 */
public final class AtlasTexture {
    public static final int SIZE = 1024;
    private static final Logger LOG = LogUtils.getLogger();
    private static final Atlas BIOMES = new Atlas(true, new ResourceLocation(ModInfo.MOD_ID, "atlas_biomes"));
    private static final Atlas RINGS = new Atlas(false, new ResourceLocation(ModInfo.MOD_ID, "atlas_rings"));

    private AtlasTexture() {
    }

    private static final class Atlas {
        final boolean biomes;
        final ResourceLocation location;
        CompletableFuture<int[]> job;
        boolean uploaded;

        Atlas(boolean biomes, ResourceLocation location) {
            this.biomes = biomes;
            this.location = location;
        }

        ResourceLocation get() {
            if (uploaded) {
                return location;
            }
            if (job == null) {
                job = CompletableFuture.supplyAsync(() -> AtlasPainter.paint(SIZE, biomes), Util.backgroundExecutor());
            }
            upload();
            return uploaded ? location : null;
        }

        void upload() {
            if (uploaded || job == null || !job.isDone()) {
                return;
            }
            int[] pixels;
            try {
                pixels = job.join();
            } catch (RuntimeException e) {
                LOG.error("Painting the world map failed", e);
                job = null;
                return;
            }
            DynamicTexture texture = new DynamicTexture(SIZE, SIZE, false);
            NativeImage image = texture.getPixels();
            if (image == null) {
                return;
            }
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    image.setPixelRGBA(x, y, MapMath.argbToAbgr(pixels[y * SIZE + x]));
                }
            }
            texture.upload();
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(location, texture);
            uploaded = true;
        }
    }

    /** The atlas texture, or null while it is still being painted (painting starts with the first call). */
    public static ResourceLocation get(boolean biomes) {
        return (biomes ? BIOMES : RINGS).get();
    }

    /** Client tick: uploads a finished painting. */
    public static void tick() {
        BIOMES.upload();
        RINGS.upload();
    }
}
