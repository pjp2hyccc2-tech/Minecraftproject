package com.eternalrealm.client.map;

import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.map.MapMath;
import com.eternalrealm.world.map.ScrollingWindow;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The minimap's state: whether it is shown, its zoom, and the {@link #TEXTURE} with the map colours of the
 * {@link #SIZE}×{@link #SIZE} blocks around the player. The texture wraps around ({@link ScrollingWindow}): world
 * block (x, z) always sits at texel (x mod {@value #SIZE}, z mod {@value #SIZE}), so walking paints only the strip
 * that comes into view and new chunk colours from {@link ChunkColors} paint only their chunk (the owner, 2026-11-13:
 * lighter while walking); the overlay draws it with texture coordinates x / size, z / size. At most every other tick.
 * Client thread only.
 */
public final class Minimap {
    /** Blocks (and texels) along each side of the texture. */
    public static final int SIZE = 256;
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "minimap");
    /** View radius in blocks of each zoom step (0 = closest). */
    private static final int[] ZOOM_RADIUS = {32, 48, 64, 96, 128};
    private static final Logger LOG = LogUtils.getLogger();
    private static final ChunkColors COLORS = new ChunkColors();
    private static final int GL_TEXTURE_2D = 3553;
    private static final int GL_TEXTURE_WRAP_S = 10242;
    private static final int GL_TEXTURE_WRAP_T = 10243;
    private static final int GL_REPEAT = 10497;
    private static DynamicTexture texture;
    private static boolean failed;
    /** Corner of the world window the texture holds, and whether it holds one. */
    private static int windowX;
    private static int windowZ;
    private static boolean drawn;
    private static boolean repaintAll = true;
    private static final Set<Long> PENDING = new HashSet<>();
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

    /** World block at texture coordinate 0: the texture repeats every {@link #SIZE} blocks from the world origin. */
    public static int originX() {
        return 0;
    }

    public static int originZ() {
        return 0;
    }

    /** Whether the texture holds a picture yet. */
    public static boolean ready() {
        return texture != null && drawn;
    }

    /** Showing the floor at the player's height (dungeons, caves). */
    public static boolean caveMode() {
        return COLORS.caveMode();
    }

    /** Client tick: computes chunk colours around the player and paints what changed into the texture. */
    public static void tick(Minecraft minecraft) {
        if (failed || minecraft.level == null || minecraft.player == null || !visible()) {
            return;
        }
        try {
            long time = minecraft.level.getGameTime();
            COLORS.tick(minecraft.level, minecraft.player, time, drawn ? 5 : 10);
            repaintAll |= COLORS.takeAllChanged();
            COLORS.drainChanged(PENDING::add);
            int x = minecraft.player.getBlockX() - SIZE / 2;
            int z = minecraft.player.getBlockZ() - SIZE / 2;
            boolean moved = x != windowX || z != windowZ;
            if ((moved || repaintAll || !PENDING.isEmpty() || !drawn) && (time - lastDraw >= 2 || time < lastDraw)) {
                paint(minecraft, x, z);
                lastDraw = time;
            }
        } catch (RuntimeException e) {
            failed = true;
            LOG.error("Minimap disabled after an error", e);
        }
    }

    /** Moves the window's corner to (x, z): paints what came into view and the chunks whose colours changed. */
    private static void paint(Minecraft minecraft, int x, int z) {
        if (texture == null) {
            texture = new DynamicTexture(SIZE, SIZE, true);
            minecraft.getTextureManager().register(TEXTURE, texture);
            texture.bind();
            RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT); // the window wraps around
            RenderSystem.texParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            repaintAll = true;
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        List<int[]> strips = drawn && !repaintAll ? ScrollingWindow.entering(windowX, windowZ, x, z, SIZE) : null;
        windowX = x;
        windowZ = z;
        if (strips == null) {
            paintRect(image, new int[]{x, z, x + SIZE, z + SIZE});
            texture.upload();
            repaintAll = false;
            PENDING.clear();
            drawn = true;
            return;
        }
        for (long key : PENDING) {
            int chunkX = ChunkPos.getX(key) << 4;
            int chunkZ = ChunkPos.getZ(key) << 4;
            int[] chunk = ScrollingWindow.clip(new int[]{chunkX, chunkZ, chunkX + 16, chunkZ + 16}, x, z, SIZE);
            if (chunk != null) {
                strips.add(chunk);
            }
        }
        PENDING.clear();
        texture.bind();
        for (int[] rect : strips) {
            paintRect(image, rect);
            for (int[] texels : ScrollingWindow.texels(rect, SIZE)) {
                image.upload(0, texels[0], texels[1], texels[0], texels[1], texels[2], texels[3], false, false);
            }
        }
    }

    /** Writes the colours of a world rectangle (end exclusive, at most the texture's size) into the image. */
    private static void paintRect(NativeImage image, int[] rect) {
        for (int z = rect[1]; z < rect[3]; z++) {
            int texelZ = Math.floorMod(z, SIZE);
            int[] colors = null;
            int colorsChunkX = Integer.MIN_VALUE;
            for (int x = rect[0]; x < rect[2]; x++) {
                if ((x >> 4) != colorsChunkX) {
                    colorsChunkX = x >> 4;
                    colors = COLORS.get(colorsChunkX, z >> 4);
                }
                int argb = colors == null ? 0 : colors[(z & 15) * 16 + (x & 15)];
                image.setPixelRGBA(Math.floorMod(x, SIZE), texelZ, MapMath.argbToAbgr(argb));
            }
        }
    }

    /** Leaving the world: forget every colour (the texture is kept for the next world). */
    public static void clear() {
        COLORS.clear();
        drawn = false;
        repaintAll = true;
        PENDING.clear();
        lastDraw = 0;
        if (texture != null && texture.getPixels() != null) {
            texture.getPixels().fillRect(0, 0, SIZE, SIZE, 0);
            texture.upload();
        }
    }
}
