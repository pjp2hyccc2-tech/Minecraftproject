package com.hitori.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.hitori.Hitori;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.OniDoll;
import com.hitori.network.S2C;
import com.hitori.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The cameras' recordings (docs/DESIGN.md §3.1): a frame of the feed on screen every three seconds while the app is
 * open, and at once when the server tags an event in view; the endings' last feeds, taken under the black / white
 * screen from a camera (or a virtual one) the player is not looking through ({@link #start}). Frames are grabbed before
 * the HUD is drawn, shrunk to 320×180, graded like a cheap action cam's night footage on a worker thread, saved as PNG
 * under {@code <game dir>/hitori/recordings/<world>/} with an index, and kept as textures for the playback.
 */
public final class FrameCapture {
    public static final int W = 320, H = 180;
    private static final int TWIN_ID = -77123;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "hitori-recording");
        t.setDaemon(true);
        return t;
    });

    /** One recorded frame: its id (file name), the camera, the night's clock, an event label ("" for none). */
    public record Frame(String id, int camera, float clock, String label) {}

    private record Grab(String id, int camera, float clock, String label, boolean ir, @Nullable String finalLabel) {}

    private static final List<Frame> FRAMES = new ArrayList<>();
    private static final Map<String, List<String>> FINALS = new LinkedHashMap<>();
    private static final Map<String, ResourceLocation> TEX = new HashMap<>();
    @Nullable private static Grab pending;
    private static int recordIn = 30;
    private static int counter;
    private static boolean loaded;
    private static String loadedWorld = "";

    // the endings' captures
    @Nullable private static S2C.Capture job;
    private static int jobFrame, jobWait;
    @Nullable private static FixedCamera virtualCam;
    @Nullable private static Entity jobView;
    @Nullable private static RemotePlayer twin;
    /** The capture's view is dark: the camera's IR (night vision on the view, and on the player without shaders). */
    private static boolean jobIr, jobPlayerNv;

    private FrameCapture() {}

    // ================================================================================================ storage
    static Path dir() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("hitori").resolve("recordings").resolve(worldName(mc));
    }

    static String worldName(Minecraft mc) {
        String name = "world";
        try {
            IntegratedServer sp = mc.getSingleplayerServer();
            if (sp != null) {
                Path root = sp.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                if (root.getFileName() != null) name = root.getFileName().toString();
            } else if (mc.getCurrentServer() != null) {
                name = "server_" + mc.getCurrentServer().ip;
            }
        } catch (Exception ignored) {
        }
        StringBuilder b = new StringBuilder();
        for (char c : name.toCharArray()) b.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
        return b.length() == 0 ? "world" : b.toString();
    }

    /** The night's frames (loaded from disk the first time in a world). */
    public static List<Frame> frames() {
        ensureLoaded();
        return FRAMES;
    }

    public static List<String> finals(String label) {
        return FINALS.getOrDefault(label, List.of());
    }

    private static void ensureLoaded() {
        Minecraft mc = Minecraft.getInstance();
        String w = worldName(mc);
        if (loaded && w.equals(loadedWorld)) return;
        loaded = true;
        loadedWorld = w;
        FRAMES.clear();
        try {
            Path idx = dir().resolve("index.txt");
            if (Files.exists(idx)) {
                for (String line : Files.readAllLines(idx, StandardCharsets.UTF_8)) {
                    String[] p = line.split(";", -1);
                    if (p.length < 4) continue;
                    FRAMES.add(new Frame(p[0], Integer.parseInt(p[1]), Float.parseFloat(p[2]), p[3]));
                }
            }
        } catch (Exception e) {
            Hitori.LOGGER.warn("[hitori] could not read the recordings index: {}", e.toString());
        }
    }

    /** A new night: the old recordings are gone. */
    public static void clear() {
        FRAMES.clear();
        FINALS.clear();
        loaded = true;
        loadedWorld = worldName(Minecraft.getInstance());
        WORKER.execute(() -> {
            try {
                Path d = dir();
                if (Files.exists(d)) {
                    try (var s = Files.list(d)) {
                        s.forEach(f -> {
                            try {
                                Files.deleteIfExists(f);
                            } catch (Exception ignored) {
                            }
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    @Nullable
    public static ResourceLocation texture(String id) {
        ResourceLocation rl = TEX.get(id);
        if (rl != null) return rl;
        Path f = dir().resolve(id + ".png");
        if (!Files.exists(f)) return null;
        try (InputStream in = Files.newInputStream(f)) {
            NativeImage img = NativeImage.read(in);
            return register(id, img);
        } catch (Exception e) {
            return null;
        }
    }

    private static ResourceLocation register(String id, NativeImage img) {
        ResourceLocation rl = Hitori.id("rec/" + id.toLowerCase(java.util.Locale.ROOT));
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        TEX.put(id, rl);
        return rl;
    }

    // ================================================================================================ the feed
    /** While the app shows a camera: a frame every three seconds. */
    public static void feedTick(Minecraft mc, int camera, int openTicks) {
        if (openTicks < 8) return;
        if (--recordIn <= 0) {
            recordIn = 60;
            request(camera, "");
        }
    }

    /** The server tagged an event in view of the watched camera: a frame now. */
    public static void tag(int camera, String label) {
        if (!CameraApp.viewing() || CameraApp.number() != camera) return;
        request(camera, label);
        recordIn = 30;
    }

    private static void request(int camera, String label) {
        if (pending != null) return;
        String id = String.format(java.util.Locale.ROOT, "c%d_%05d", camera, counter++ % 100000);
        pending = new Grab(id, camera, ClientState.clock, label, CameraApp.ir(), null);
    }

    // ================================================================================================ the endings' last feeds
    /** S2C.Capture: look through that camera (or a virtual one) and take frames under the ending's screen. */
    public static void start(S2C.Capture c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        CameraApp.forceClose();
        Peek.reset();
        job = c;
        jobFrame = 0;
        jobWait = 2;
        FINALS.put(c.label(), new ArrayList<>());
        Entity view = null;
        if (c.number() > 0) {
            for (FixedCamera f : CameraApp.cameras(mc)) if (f.number() == c.number()) view = f;
        }
        if (view == null) {
            if (virtualCam == null || virtualCam.level() != mc.level) {
                virtualCam = ModEntities.CAMERA.get().create(mc.level);
                if (virtualCam != null) virtualCam.clientOnly = true;
            }
            if (virtualCam != null) virtualCam.place(new Vec3(c.x(), c.y(), c.z()), c.yaw(), c.pitch());
            view = virtualCam;
        }
        jobView = view;
        if (view != null) mc.setCameraEntity(view);
        if (c.label().equals("you_tub")) spawnTwin(mc, view);
        // like the app: in the dark the camera sees in IR
        int light = mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, net.minecraft.core.BlockPos.containing(c.x(), c.y(), c.z()));
        jobIr = light < 7;
        if (jobIr) {
            if (view instanceof net.minecraft.world.entity.LivingEntity le) le.forceAddEffect(nightVision(), null);
            if (!ShaderCompat.active() && !mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
                mc.player.forceAddEffect(nightVision(), null);
                jobPlayerNv = true;
            }
        }
    }

    private static net.minecraft.world.effect.MobEffectInstance nightVision() {
        return new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 100000, 0, false, false, false);
    }

    /** A figure that is you, standing in the bathtub, facing the lens. */
    private static void spawnTwin(Minecraft mc, @Nullable Entity lens) {
        try {
            GameProfile me = mc.player.getGameProfile();
            GameProfile profile = new GameProfile(UUID.randomUUID(), me.getName());
            UUID self = mc.player.getUUID();
            twin = new RemotePlayer(mc.level, profile) {
                @Nullable
                @Override
                protected PlayerInfo getPlayerInfo() {
                    return mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(self);
                }
            };
            Vec3 at = OniDoll.tubFloor(0);
            float yaw = 0.0f;
            if (lens != null) {
                double dx = lens.getX() - at.x, dz = lens.getZ() - at.z;
                yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            }
            twin.moveTo(at.x, at.y, at.z, yaw, 12.0f);
            twin.setYHeadRot(yaw + 8.0f);
            twin.yBodyRot = yaw;
            twin.yBodyRotO = yaw;
            twin.yHeadRotO = yaw + 8.0f;
            twin.setId(TWIN_ID);
            mc.level.putNonPlayerEntity(TWIN_ID, twin);
        } catch (Throwable t) {
            Hitori.LOGGER.warn("[hitori] could not place the twin: {}", t.toString());
            twin = null;
        }
    }

    public static boolean capturing() {
        return job != null;
    }

    /** The twin in the tub is you, but nobody's name floats over it. */
    public static boolean isTwin(Entity e) {
        return twin != null && e == twin;
    }

    public static void tick(Minecraft mc) {
        if (job == null) return;
        if (jobView != null && mc.getCameraEntity() != jobView) mc.setCameraEntity(jobView);
        if (pending != null) return;
        if (--jobWait > 0) return;
        if (jobFrame >= job.frames()) {
            finishJob(mc);
            return;
        }
        String id = String.format(java.util.Locale.ROOT, "final_%s_%d", job.label(), jobFrame);
        pending = new Grab(id, Math.max(0, job.number()), ClientState.clock, job.label(), jobIr, job.label());
        jobFrame++;
        jobWait = Math.max(1, job.interval());
    }

    private static void finishJob(Minecraft mc) {
        job = null;
        if (jobView instanceof net.minecraft.world.entity.LivingEntity le && le != mc.player) le.removeEffectNoUpdate(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        if (jobPlayerNv && mc.player != null) mc.player.removeEffectNoUpdate(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
        jobPlayerNv = false;
        jobView = null;
        if (twin != null && mc.level != null) {
            mc.level.removeEntity(TWIN_ID, Entity.RemovalReason.DISCARDED);
            twin = null;
        }
        if (mc.player != null) mc.setCameraEntity(mc.player);
    }

    // ================================================================================================ the grab
    /** Before the HUD is drawn: the frame is the world alone. */
    public static void onRenderGuiPre(Minecraft mc) {
        CameraApp.onRenderGuiPre(mc);
        if (pending == null) return;
        Grab g = pending;
        pending = null;
        if (g.finalLabel() == null && !CameraApp.viewing()) return;
        try {
            NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            int w = img.getWidth(), h = img.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = img.getPixelRGBA(x, y);
            img.close();
            boolean shaders = ShaderCompat.active();
            long seed = g.id().hashCode() * 31L + System.nanoTime();
            WORKER.execute(() -> {
                NativeImage out = null;
                try {
                    int[] graded = grade(px, w, h, g.ir() && !shaders, seed);
                    out = new NativeImage(W, H, false);
                    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) out.setPixelRGBA(x, y, graded[y * W + x]);
                    Path f = dir().resolve(g.id() + ".png");
                    Files.createDirectories(f.getParent());
                    out.writeToFile(f);
                    if (g.finalLabel() == null) {
                        Files.writeString(dir().resolve("index.txt"), g.id() + ";" + g.camera() + ";" + g.clock() + ";" + g.label() + "\n",
                                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    }
                } catch (Throwable t) {
                    Hitori.LOGGER.error("[hitori] saving a recording frame failed", t);
                }
                NativeImage done = out;
                mc.execute(() -> {
                    if (done == null) return;
                    register(g.id(), done);
                    if (g.finalLabel() != null) FINALS.computeIfAbsent(g.finalLabel(), k -> new ArrayList<>()).add(g.id());
                    else {
                        ensureLoaded();
                        FRAMES.add(new Frame(g.id(), g.camera(), g.clock(), g.label()));
                    }
                });
            });
        } catch (Throwable t) {
            Hitori.LOGGER.error("[hitori] could not grab the frame", t);
        }
    }

    /** A cheap action camera at night: crop to 16:9, shrink, IR monochrome in the dark, crushed blacks, noise, a soft blur. */
    static int[] grade(int[] src, int w, int h, boolean ir, long seed) {
        Random rnd = new Random(seed);
        int cw = w, ch = Math.round(w * 9.0f / 16.0f);
        if (ch > h) {
            ch = h;
            cw = Math.round(h * 16.0f / 9.0f);
        }
        int x0 = (w - cw) / 2, y0 = (h - ch) / 2;
        float[] r = new float[W * H], g = new float[W * H], b = new float[W * H];
        for (int oy = 0; oy < H; oy++) {
            int sy0 = y0 + oy * ch / H, sy1 = Math.max(sy0 + 1, y0 + (oy + 1) * ch / H);
            for (int ox = 0; ox < W; ox++) {
                int sx0 = x0 + ox * cw / W, sx1 = Math.max(sx0 + 1, x0 + (ox + 1) * cw / W);
                float sr = 0, sg = 0, sb = 0;
                int n = 0;
                for (int sy = sy0; sy < sy1; sy += 2) {
                    for (int sx = sx0; sx < sx1; sx += 2) {
                        int c = src[sy * w + sx];
                        sr += c & 0xFF;
                        sg += (c >> 8) & 0xFF;
                        sb += (c >> 16) & 0xFF;
                        n++;
                    }
                }
                int i = oy * W + ox;
                r[i] = sr / n / 255.0f;
                g[i] = sg / n / 255.0f;
                b[i] = sb / n / 255.0f;
            }
        }
        int[] out = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                float cr = r[i], cg = g[i], cb = b[i];
                float lum = 0.3f * cr + 0.59f * cg + 0.11f * cb;
                if (ir) {
                    float l = (float) Math.pow(Math.min(1.0f, lum * 1.15f), 0.85);
                    cr = l * 0.86f;
                    cg = l * 0.97f;
                    cb = l * 0.88f;
                } else {
                    cr = lum + (cr - lum) * 0.7f;
                    cg = lum + (cg - lum) * 0.7f;
                    cb = lum + (cb - lum) * 0.7f;
                }
                float nx = (x + 0.5f) / W * 2 - 1, ny = (y + 0.5f) / H * 2 - 1;
                float v = 1.0f - 0.35f * (nx * nx * 0.8f + ny * ny);
                float n = (float) rnd.nextGaussian() * 0.045f;
                float line = (y % 2 == 0) ? 0.97f : 1.0f;
                cr = (cr * v + n) * line;
                cg = (cg * v + n) * line;
                cb = (cb * v + n) * line;
                int ir8 = Math.max(0, Math.min(255, Math.round(cr * 255))), ig8 = Math.max(0, Math.min(255, Math.round(cg * 255)));
                int ib8 = Math.max(0, Math.min(255, Math.round(cb * 255)));
                out[i] = 0xFF000000 | (ib8 << 16) | (ig8 << 8) | ir8;
            }
        }
        return out;
    }

    public static void reset() {
        pending = null;
        job = null;
        jobView = null;
        jobPlayerNv = false;
        twin = null;
        loaded = false;
        TEX.clear();
    }
}
