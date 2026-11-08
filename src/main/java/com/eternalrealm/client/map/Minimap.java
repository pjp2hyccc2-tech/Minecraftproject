package com.eternalrealm.client.map;

import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.map.MapMath;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * The minimap's state: whether it is shown, its zoom, and the {@link #TEXTURE} with the map colours of the
 * {@link #SIZE}×{@link #SIZE} blocks around the player, redrawn from {@link ChunkColors} when the player moves to
 * another block or new chunk colours arrive (at most every other tick). Client thread only.
 */
public final class Minimap {
    /** Blocks (and texels) along each side of the texture. */
    public static final int SIZE = 256;
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "minimap");
    /** View radius in blocks of each zoom step (0 = closest). */
    private static final int[] ZOOM_RADIUS = {32, 48, 64, 96, 128};
    private static final Logger LOG = LogUtils.getLogger();
    private static final ChunkColors COLORS = new ChunkColors();
    private static DynamicTexture texture;
    private static boolean failed;
    private static int originX;
    private static int originZ;
    private static int drawnVersion = -1;
    private static int drawnX = Integer.MIN_VALUE;
    private static int drawnZ = Integer.MIN_VALUE;
    private static long lastDraw;

    private Minimap() {
    }

    public static boolean visible() {
        return !failed && config(true);
    }

    /** Toggle key: shows or hides the minimap (saved in the client config). */
    public static void toggle() {
        boolean show = !config(true);
        try {
            ERClientConfig.MINIMAP_ENABLED.set(show);
            ERClientConfig.MINIMAP_ENABLED.save();
        } catch (IllegalStateException e) {
            // config not loaded yet: nothing to toggle
        }
    }

    /** Zoom keys: {@code step} -1 zooms in, +1 out (saved in the client config). */
    public static void zoom(int step) {
        try {
            int zoom = Math.max(0, Math.min(ZOOM_RADIUS.length - 1, ERClientConfig.MINIMAP_ZOOM.get() + step));
            ERClientConfig.MINIMAP_ZOOM.set(zoom);
            ERClientConfig.MINIMAP_ZOOM.save();
        } catch (IllegalStateException e) {
            // config not loaded yet
        }
    }

    /** View radius in blocks at the configured zoom. */
    public static int radiusBlocks() {
        int zoom;
        try {
            zoom = ERClientConfig.MINIMAP_ZOOM.get();
        } catch (IllegalStateException e) {
            zoom = 2;
        }
        return ZOOM_RADIUS[Math.max(0, Math.min(ZOOM_RADIUS.length - 1, zoom))];
    }

    private static boolean config(boolean fallback) {
        try {
            return ERClientConfig.MINIMAP_ENABLED.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    /** World block at the texture's texel (0, 0). */
    public static int originX() {
        return originX;
    }

    public static int originZ() {
        return originZ;
    }

    /** Whether the texture holds a picture yet. */
    public static boolean ready() {
        return texture != null && drawnVersion >= 0;
    }

    /** Showing the floor at the player's height (dungeons, caves). */
    public static boolean caveMode() {
        return COLORS.caveMode();
    }

    /** Client tick: computes chunk colours around the player and redraws the texture when needed. */
    public static void tick(Minecraft minecraft) {
        if (failed || minecraft.level == null || minecraft.player == null || !visible()) {
            return;
        }
        try {
            long time = minecraft.level.getGameTime();
            COLORS.tick(minecraft.level, minecraft.player, time, drawnVersion < 0 ? 10 : 5);
            int x = minecraft.player.getBlockX();
            int z = minecraft.player.getBlockZ();
            boolean moved = x != drawnX || z != drawnZ;
            if ((moved || COLORS.version() != drawnVersion) && (time - lastDraw >= 2 || time < lastDraw)) {
                redraw(minecraft, x, z);
                lastDraw = time;
            }
        } catch (RuntimeException e) {
            failed = true;
            LOG.error("Minimap disabled after an error", e);
        }
    }

    private static void redraw(Minecraft minecraft, int centerX, int centerZ) {
        if (texture == null) {
            texture = new DynamicTexture(SIZE, SIZE, true);
            minecraft.getTextureManager().register(TEXTURE, texture);
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        int newOriginX = centerX - SIZE / 2;
        int newOriginZ = centerZ - SIZE / 2;
        for (int chunkZ = newOriginZ >> 4; chunkZ <= (newOriginZ + SIZE - 1) >> 4; chunkZ++) {
            for (int chunkX = newOriginX >> 4; chunkX <= (newOriginX + SIZE - 1) >> 4; chunkX++) {
                int[] colors = COLORS.get(chunkX, chunkZ);
                for (int localZ = 0; localZ < 16; localZ++) {
                    int texelZ = (chunkZ << 4) + localZ - newOriginZ;
                    if (texelZ < 0 || texelZ >= SIZE) {
                        continue;
                    }
                    for (int localX = 0; localX < 16; localX++) {
                        int texelX = (chunkX << 4) + localX - newOriginX;
                        if (texelX < 0 || texelX >= SIZE) {
                            continue;
                        }
                        int argb = colors == null ? 0 : colors[localZ * 16 + localX];
                        image.setPixelRGBA(texelX, texelZ, MapMath.argbToAbgr(argb));
                    }
                }
            }
        }
        texture.upload();
        originX = newOriginX;
        originZ = newOriginZ;
        drawnX = centerX;
        drawnZ = centerZ;
        drawnVersion = COLORS.version();
    }

    /** Leaving the world: forget every colour (the texture is kept for the next world). */
    public static void clear() {
        COLORS.clear();
        drawnVersion = -1;
        drawnX = Integer.MIN_VALUE;
        drawnZ = Integer.MIN_VALUE;
        lastDraw = 0;
        if (texture != null && texture.getPixels() != null) {
            texture.getPixels().fillRect(0, 0, SIZE, SIZE, 0);
            texture.upload();
        }
    }
}
