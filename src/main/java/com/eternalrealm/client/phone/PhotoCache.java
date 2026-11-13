package com.eternalrealm.client.phone;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.network.forge.PhotoDataPacket;
import com.eternalrealm.social.phone.PhotoCodec;
import com.eternalrealm.social.phone.PhotoRules;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Photos of the 魔導スマホ on the client: asked from the server when first shown ({@code phone.photo}), decoded off the
 * render thread, kept as textures for the {@value #KEEP} last shown. Client thread only.
 */
public final class PhotoCache {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int KEEP = 32;
    private static final int MAX_ASKING = 4;
    private static final long RETRY_MILLIS = 15_000L;

    /** A photo: loading, ready (a texture of {@code width} x {@code height}), or missing (gone, not allowed). */
    public static final class Photo {
        private ResourceLocation texture;
        private DynamicTexture dynamic;
        private int width;
        private int height;
        private boolean missing;
        private long askedAt;

        public boolean ready() {
            return texture != null;
        }

        public boolean missing() {
            return missing;
        }

        public ResourceLocation texture() {
            return texture;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }
    }

    private static final LinkedHashMap<String, Photo> PHOTOS = new LinkedHashMap<>(64, 0.75F, true);
    private static int asking;
    private static int counter;

    private PhotoCache() {
    }

    /** The photo (asked for the first time it is wanted). */
    public static Optional<Photo> get(String id) {
        if (!PhotoRules.validId(id)) {
            return Optional.empty();
        }
        Photo photo = PHOTOS.get(id);
        long now = System.currentTimeMillis();
        if (photo == null) {
            photo = new Photo();
            PHOTOS.put(id, photo);
            evict();
        }
        if (!photo.ready() && !photo.missing() && now - photo.askedAt > RETRY_MILLIS && asking < MAX_ASKING) {
            photo.askedAt = now;
            asking++;
            JsonObject args = new JsonObject();
            args.addProperty("id", id);
            ClientPhone.send("phone.photo", args);
        }
        return Optional.of(photo);
    }

    /** A photo the server sent (or refused: no bytes). */
    public static void accept(PhotoDataPacket packet) {
        asking = Math.max(0, asking - 1);
        Photo photo = PHOTOS.get(packet.id());
        if (photo == null) {
            return;
        }
        if (packet.data().length == 0) {
            photo.missing = true;
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return PhotoCodec.read(packet.data());
            } catch (IOException e) {
                LOG.debug("Could not read photo {}: {}", packet.id(), e.getMessage());
                return null;
            }
        }, Util.backgroundExecutor()).thenAcceptAsync(image -> {
            if (PHOTOS.get(packet.id()) != photo) {
                return; // evicted or cleared meanwhile
            }
            if (image == null) {
                photo.missing = true;
                return;
            }
            upload(photo, image, "phone_photo/" + packet.id());
        }, Minecraft.getInstance());
    }

    /** Makes a texture of {@code image} for {@code photo} (render thread). */
    static void upload(Photo photo, BufferedImage image, String path) {
        DynamicTexture texture = new DynamicTexture(image.getWidth(), image.getHeight(), false);
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            photo.missing = true;
            return;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                // NativeImage holds ABGR
                pixels.setPixelRGBA(x, y, 0xFF000000 | (argb & 0xFF) << 16 | (argb & 0xFF00) | (argb >> 16 & 0xFF));
            }
        }
        texture.upload();
        texture.setFilter(true, false);
        ResourceLocation location = new ResourceLocation(ModInfo.MOD_ID, path + "_" + (counter++));
        Minecraft.getInstance().getTextureManager().register(location, texture);
        photo.texture = location;
        photo.dynamic = texture;
        photo.width = image.getWidth();
        photo.height = image.getHeight();
    }

    /** A texture for a photo the player just took (not from the server). */
    static Photo local(BufferedImage image) {
        Photo photo = new Photo();
        upload(photo, image, "phone_capture");
        return photo;
    }

    static void release(Photo photo) {
        if (photo != null && photo.texture != null) {
            Minecraft.getInstance().getTextureManager().release(photo.texture);
            photo.texture = null;
            photo.dynamic = null;
        }
    }

    private static void evict() {
        Iterator<Map.Entry<String, Photo>> it = PHOTOS.entrySet().iterator();
        while (PHOTOS.size() > KEEP && it.hasNext()) {
            release(it.next().getValue());
            it.remove();
        }
    }

    public static void clear() {
        for (Photo photo : PHOTOS.values()) {
            release(photo);
        }
        PHOTOS.clear();
        asking = 0;
    }
}
