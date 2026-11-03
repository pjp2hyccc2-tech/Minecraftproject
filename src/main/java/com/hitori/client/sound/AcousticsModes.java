package com.hitori.client.sound;

import com.hitori.Hitori;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * The listening modes of {@link Acoustics} (docs/DESIGN.md §10), resolved once per client tick on the client thread:
 * <ul>
 * <li><b>closet</b> ({@link #enclosure()} 0..1): the player shut in a hiding place;</li>
 * <li><b>camera</b> ({@link #cameraView()}): the camera app is open, the listener is a fixed camera; the camera
 * microphone's hiss ({@code camera.mic_hiss}) plays while it lasts.</li>
 * </ul>
 * Source of truth, in order: the game's {@link AcousticsState} ({@link Acoustics#setState}), the game's
 * {@code com.hitori.client.ClientState} read reflectively, then the engine's own observation (layout room + fusuma
 * state, camera entity). The hiss plays even when the EFX part is off or broken; it never throws.
 */
final class AcousticsModes {
    private static final String CLIENT_STATE = "com.hitori.client.ClientState";
    private static final String[] CLOSET_NAMES = {"inCloset", "isInCloset", "hiding", "isHiding", "inHiding"};
    private static final String[] PEEK_NAMES = {"peeking", "isPeeking"};
    private static final String[] CAMERA_NAMES = {"cameraView", "isCameraView", "cameraViewing", "inCameraView", "cameraOpen", "isCameraOpen"};

    static volatile AcousticsState api;
    static volatile boolean micHiss = true;

    private static boolean looked;
    private static Method mCloset, mPeek, mCamera;
    private static float enclosure;
    private static boolean camera;
    private static String source = "none";
    private static HissSound hiss;
    private static int hissRetry;

    private AcousticsModes() {}

    static float enclosure() {
        return enclosure;
    }

    static boolean cameraView() {
        return camera;
    }

    /** Where the modes came from (F3 line). */
    static String source() {
        return source;
    }

    /** Resolves both modes for this tick; returns true when either changed noticeably. */
    static boolean tick(Minecraft mc, boolean inDimension) {
        float enc = 0f;
        boolean cam = false;
        if (inDimension && mc.player != null) {
            AcousticsState s = api;
            boolean done = false;
            if (s != null) {
                try {
                    enc = clamp01(s.enclosure());
                    cam = s.cameraView();
                    source = "game";
                    done = true;
                } catch (Throwable t) {
                    Hitori.LOGGER.warn("[hitori] acoustics: the game's AcousticsState failed, using the fallback", t);
                    api = null;
                }
            }
            if (!done && reflect()) {
                try {
                    boolean closet = call(mCloset), peek = call(mPeek);
                    enc = closet ? (peek ? 0.5f : 1f) : 0f;
                    cam = call(mCamera);
                    source = "ClientState";
                    done = true;
                } catch (Throwable t) {
                    mCloset = mPeek = mCamera = null;
                }
            }
            if (!done) {
                enc = observeCloset(mc);
                Entity ce = mc.getCameraEntity();
                cam = ce != null && ce != mc.player;
                source = "observed";
            }
        } else {
            source = "none";
        }
        boolean changed = Math.abs(enc - enclosure) > 0.05f || cam != camera;
        enclosure = enc;
        camera = cam;
        updateHiss(mc, cam && inDimension);
        return changed;
    }

    // ------------------------------------------------------------------------------------------------------------

    private static boolean reflect() {
        if (!looked) {
            looked = true;
            try {
                Class<?> c = Class.forName(CLIENT_STATE, false, AcousticsModes.class.getClassLoader());
                mCloset = find(c, CLOSET_NAMES);
                mPeek = find(c, PEEK_NAMES);
                mCamera = find(c, CAMERA_NAMES);
                if (mCloset != null || mCamera != null) {
                    Hitori.LOGGER.info("[hitori] acoustics: reading the closet / camera state from {} ({}, {}, {})", CLIENT_STATE,
                            mCloset == null ? "-" : mCloset.getName(), mPeek == null ? "-" : mPeek.getName(),
                            mCamera == null ? "-" : mCamera.getName());
                }
            } catch (Throwable ignored) {
                // no such class (yet): observe by ourselves
            }
        }
        return mCloset != null || mCamera != null;
    }

    private static Method find(Class<?> c, String[] names) {
        for (String n : names) {
            try {
                Method m = c.getMethod(n);
                Class<?> r = m.getReturnType();
                if (Modifier.isStatic(m.getModifiers()) && (r == boolean.class || r == Boolean.class)) return m;
            } catch (NoSuchMethodException ignored) {
                // try the next name
            }
        }
        return null;
    }

    private static boolean call(Method m) throws ReflectiveOperationException {
        if (m == null) return false;
        Object o = m.invoke(null);
        return o instanceof Boolean b && b;
    }

    /** The player's eyes in the layout's closet room: 1 behind a shut fusuma, 0.5 with it open a crack, 0 when open. */
    private static float observeCloset(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return 0f;
        Vec3 eye = mc.player.getEyePosition();
        Layout.Room room = Layout.roomAt(BlockPos.containing(eye));
        if (room == null || !"closet".equals(room.key())) return 0f;
        if (!Layout.has("closet_fusuma")) return 1f;
        int open = -1;
        List<Layout.Marker> ms = Layout.markers("closet_fusuma");
        for (Layout.Marker m : ms) {
            for (int dy = 0; dy <= 1; dy++) {
                int o = openState(level.getBlockState(m.pos().above(dy)));
                if (o > open) open = o;
            }
        }
        if (open <= 0) return 1f;   // shut (or no fusuma state found)
        return open == 1 ? 0.5f : 0f;
    }

    /** -1 no "open" state, 0 shut, 1 a crack, 2 open (boolean open = 2). */
    static int openState(BlockState s) {
        if (s == null || s.isAir()) return -1;
        for (Property<?> p : s.getProperties()) {
            if (!"open".equals(p.getName())) continue;
            if (p instanceof IntegerProperty ip) return Math.min(2, s.getValue(ip));
            if (p instanceof BooleanProperty bp) return s.getValue(bp) ? 2 : 0;
        }
        return -1;
    }

    // ------------------------------------------------------------------------------------------------------------
    // camera.mic_hiss

    private static void updateHiss(Minecraft mc, boolean want) {
        try {
            if (!micHiss) want = false;
            HissSound h = hiss;
            if (want) {
                if (h == null || h.isStopped() || !mc.getSoundManager().isActive(h)) {
                    if (hissRetry > 0) {
                        hissRetry--;
                        return;
                    }
                    if (!ModSounds.CAMERA_MIC_HISS.isPresent()) return;
                    h = new HissSound(ModSounds.CAMERA_MIC_HISS.get());
                    hiss = h;
                    mc.getSoundManager().play(h);
                    hissRetry = 20;
                }
                h.target = 1f;
            } else if (h != null) {
                h.target = 0f;
                if (h.isStopped()) hiss = null;
            }
        } catch (Throwable t) {
            micHiss = false;
            Hitori.LOGGER.warn("[hitori] acoustics: camera mic hiss disabled after an error", t);
        }
    }

    /** The camera microphone's hiss: a non-positional loop fading in and out with the camera view. */
    static final class HissSound extends AbstractTickableSoundInstance {
        float target = 1f;

        HissSound(SoundEvent event) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
        }

        @Override
        public void tick() {
            volume += (target - volume) * (target > volume ? 0.35f : 0.25f);
            if (target <= 0f && volume < 0.01f) {
                stop();
                return;
            }
            if (volume < 0.001f) volume = 0.001f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
