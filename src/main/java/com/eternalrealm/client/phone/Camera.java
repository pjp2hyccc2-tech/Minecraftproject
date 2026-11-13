package com.eternalrealm.client.phone;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.social.phone.PhotoCodec;
import com.eternalrealm.social.phone.PhotoRules;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * The 魔導スマホ's camera: closes the phone, waits a couple of frames (with the HUD hidden if asked), takes the frame
 * (the vanilla screenshot code, optionally also saved like F2), makes it {@link PhotoRules#WIDTH} x
 * {@link PhotoRules#HEIGHT} and JPEG off the render thread, and opens the phone again on it. The last
 * {@value #KEEP} photos stay on hand until leaving the world. Client thread only.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Camera {
    private static final Logger LOG = LogUtils.getLogger();
    public static final int KEEP = 8;
    private static final int WAIT_FRAMES = 3;

    /** A photo taken here: its picture, its texture, its JPEG (once made) and its id on the server (once sent). */
    public static final class Shot {
        private final int number;
        private final BufferedImage image;
        private final PhotoCache.Photo preview;
        private byte[] jpeg;
        private String serverId = "";

        Shot(int number, BufferedImage image) {
            this.number = number;
            this.image = image;
            this.preview = PhotoCache.local(image);
        }

        public int number() {
            return number;
        }

        public PhotoCache.Photo preview() {
            return preview;
        }

        public Optional<byte[]> jpeg() {
            return Optional.ofNullable(jpeg);
        }

        public String serverId() {
            return serverId;
        }

        void sent(String id) {
            serverId = id;
        }

        /** Its id was used in a post or a message: a second use sends it again. */
        public void used() {
            serverId = "";
        }

        BufferedImage image() {
            return image;
        }
    }

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int frames = -1;
    private static boolean restoreHud;
    private static boolean wasHidden;
    private static boolean save;
    private static int numbers = 1;
    private static boolean busy;

    private Camera() {
    }

    /** Takes a photo in a moment: the phone closes, the HUD hides if {@code hideHud}; saved like F2 if {@code saveCopy}. */
    public static void take(boolean hideHud, boolean saveCopy) {
        Minecraft minecraft = Minecraft.getInstance();
        if (busy || minecraft.level == null) {
            return;
        }
        busy = true;
        save = saveCopy;
        minecraft.setScreen(null);
        wasHidden = minecraft.options.hideGui;
        restoreHud = hideHud && !wasHidden;
        if (restoreHud) {
            minecraft.options.hideGui = true;
        }
        frames = WAIT_FRAMES;
    }

    public static boolean busy() {
        return busy;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || frames < 0) {
            return;
        }
        if (frames-- > 0) {
            return;
        }
        frames = -1;
        Minecraft minecraft = Minecraft.getInstance();
        int[] pixels;
        int width;
        int height;
        try (NativeImage frame = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            width = frame.getWidth();
            height = frame.getHeight();
            pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int abgr = frame.getPixelRGBA(x, y);
                    pixels[y * width + x] = (abgr & 0xFF) << 16 | (abgr & 0xFF00) | (abgr >> 16 & 0xFF);
                }
            }
        } catch (RuntimeException e) {
            LOG.error("Taking a photo failed", e);
            done(minecraft, null);
            return;
        }
        if (save) {
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
                    message -> minecraft.execute(() -> minecraft.gui.getChat().addMessage(message)));
        }
        if (restoreHud) {
            minecraft.options.hideGui = wasHidden;
        }
        CompletableFuture.supplyAsync(() -> {
            BufferedImage full = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            full.setRGB(0, 0, width, height, pixels, 0, width);
            return PhotoCodec.fit(full, PhotoRules.WIDTH, PhotoRules.HEIGHT);
        }, Util.backgroundExecutor()).whenCompleteAsync((small, error) -> {
            if (error != null || small == null) {
                LOG.error("Making the photo small failed", error);
                done(minecraft, null);
                return;
            }
            Shot shot = new Shot(numbers++, small);
            SHOTS.add(0, shot);
            while (SHOTS.size() > KEEP) {
                PhotoCache.release(SHOTS.remove(SHOTS.size() - 1).preview());
            }
            CompletableFuture.supplyAsync(() -> {
                try {
                    return PhotoCodec.encode(small);
                } catch (IOException e) {
                    return null;
                }
            }, Util.backgroundExecutor()).thenAcceptAsync(jpeg -> shot.jpeg = jpeg, minecraft);
            done(minecraft, shot);
        }, minecraft);
    }

    private static void done(Minecraft minecraft, Shot shot) {
        busy = false;
        if (restoreHud) {
            minecraft.options.hideGui = wasHidden;
            restoreHud = false;
        }
        if (shot == null) {
            ClientPhone.toast(Component.translatable("phone.eternalrealm.camera.failed"));
        }
        com.eternalrealm.ui.phone.PhoneScreen.openCamera(shot);
    }

    /** The photos taken, the newest first. */
    public static List<Shot> shots() {
        return List.copyOf(SHOTS);
    }

    public static Optional<Shot> shot(int number) {
        for (Shot shot : SHOTS) {
            if (shot.number() == number) {
                return Optional.of(shot);
            }
        }
        return Optional.empty();
    }

    static void clear() {
        for (Shot shot : SHOTS) {
            PhotoCache.release(shot.preview());
        }
        SHOTS.clear();
        frames = -1;
        busy = false;
    }
}
