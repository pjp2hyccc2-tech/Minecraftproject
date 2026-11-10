package com.eternalrealm.ui.map;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.map.FieldMaskPainter;
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
 * The field tab's overlays ({@link FieldMaskPainter}), one per field, painted in the background the first time a
 * field is shown and then kept for the rest of the game. Render thread only (except the painting itself).
 */
public final class FieldMaskTexture {
    public static final int SIZE = 384;
    private static final Logger LOG = LogUtils.getLogger();
    @SuppressWarnings("unchecked")
    private static final CompletableFuture<int[]>[] JOBS = new CompletableFuture[FieldLayout.FIELD_COUNT + 1];
    private static final boolean[] UPLOADED = new boolean[FieldLayout.FIELD_COUNT + 1];

    private FieldMaskTexture() {
    }

    private static ResourceLocation location(int field) {
        return ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "field_mask_" + field);
    }

    /** The mask of {@code field}, or null while it is still being painted (painting starts with the first call). */
    public static ResourceLocation get(int field) {
        if (field < 1 || field > FieldLayout.FIELD_COUNT) {
            return null;
        }
        if (UPLOADED[field]) {
            return location(field);
        }
        if (JOBS[field] == null) {
            int rim = MapMath.mix(Areas.require(field).color(), 0xFFFFFF, 0.35);
            JOBS[field] = CompletableFuture.supplyAsync(() -> FieldMaskPainter.paint(field, SIZE, rim),
                    Util.backgroundExecutor());
        }
        if (!JOBS[field].isDone()) {
            return null;
        }
        int[] pixels;
        try {
            pixels = JOBS[field].join();
        } catch (RuntimeException e) {
            LOG.error("Painting the mask of field {} failed", field, e);
            JOBS[field] = null;
            return null;
        }
        DynamicTexture texture = new DynamicTexture(SIZE, SIZE, false);
        NativeImage image = texture.getPixels();
        if (image == null) {
            return null;
        }
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setPixelRGBA(x, y, MapMath.argbToAbgr(pixels[y * SIZE + x]));
            }
        }
        texture.upload();
        texture.setFilter(true, false);
        Minecraft.getInstance().getTextureManager().register(location(field), texture);
        UPLOADED[field] = true;
        return location(field);
    }
}
