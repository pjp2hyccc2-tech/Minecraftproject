package com.reisatsu.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.reisatsu.Reisatsu;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameData;
import com.reisatsu.network.C2S;
import com.reisatsu.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The photo itself (docs/DESIGN.md §3.1): the rendered frame is grabbed at the flash — world only, before the HUD is
 * drawn ({@code RenderGuiEvent.Pre}) with the hand hidden — cropped to the instant film's 4:3, shrunk to 320×240 and
 * developed like old instant film (warm shift, faded blacks, flash falloff, vignette, grain, slight blur, a light leak),
 * then finished by the server's verdict (a smeared blur with a face for a spirit that noticed you, now and then a
 * faint face in an empty shot), saved as PNG under {@code <game dir>/reisatsu/photos/<world>/<id>.png} and shown
 * ejecting from the camera.
 */
public final class PhotoCapture {
    public static final int W = 320, H = 240;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reisatsu-photo");
        t.setDaemon(true);
        return t;
    });

    private record Pending(String id, boolean flashPost) {}

    public record Result(int result, @Nullable SpiritKind spirit, int stars, int ghost) {}

    @Nullable private static Pending pending;
    private static int framesToWait;
    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Map<String, int[]> DEVELOPED = new ConcurrentHashMap<>();
    private static final Map<String, Result> RESULTS = new HashMap<>();
    private static final Set<String> MINE = new HashSet<>();
    private static int counter;

    private PhotoCapture() {}

    private static String newId() {
        java.time.LocalDateTime t = java.time.LocalDateTime.now();
        return String.format(java.util.Locale.ROOT, "p%04d%02d%02d_%02d%02d%02d_%d", t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond(), counter++ % 100);
    }

    /** The shutter: ask the server, grab the frame two frames later (when the flash lights it). */
    public static void request(Minecraft mc, float fov, float zoom) {
        if (mc.player == null) return;
        String id = newId();
        pending = new Pending(id, !ShaderCompat.active());
        framesToWait = 2;
        MINE.add(id);
        ModNetwork.sendToServer(new C2S.Shutter(id, fov, zoom, mc.player.getYRot(), mc.player.getXRot()));
    }

    /** Before the HUD is drawn: the frame is the world alone. */
    public static void onRenderGuiPre(Minecraft mc) {
        if (pending == null) return;
        if (--framesToWait > 0) return;
        Pending p = pending;
        pending = null;
        try {
            NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            int w = img.getWidth(), h = img.getHeight();
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = img.getPixelRGBA(x, y);
            img.close();
            IN_FLIGHT.add(p.id());
            long seed = p.id().hashCode() * 31L + System.nanoTime();
            WORKER.execute(() -> {
                try {
                    DEVELOPED.put(p.id(), Develop.base(px, w, h, p.flashPost(), seed));
                } catch (Throwable t) {
                    Reisatsu.LOGGER.error("[reisatsu] developing a photo failed", t);
                } finally {
                    IN_FLIGHT.remove(p.id());
                }
                mc.execute(() -> tryFinish(mc, p.id()));
            });
        } catch (Throwable t) {
            Reisatsu.LOGGER.error("[reisatsu] could not grab the frame", t);
        }
    }

    public static void onResult(String id, int result, @Nullable SpiritKind spirit, int stars, int ghost) {
        Minecraft mc = Minecraft.getInstance();
        RESULTS.put(id, new Result(result, spirit, stars, ghost));
        if (!MINE.contains(id)) {
            // a shot the server took for us (debug): grab this frame
            MINE.add(id);
            pending = new Pending(id, !ShaderCompat.active());
            framesToWait = 1;
            return;
        }
        tryFinish(mc, id);
    }

    private static void tryFinish(Minecraft mc, String id) {
        int[] base = DEVELOPED.get(id);
        Result r = RESULTS.get(id);
        if (base == null || r == null) return;
        DEVELOPED.remove(id);
        RESULTS.remove(id);
        Path file = Photos.file(id);
        long seed = id.hashCode() * 977L;
        WORKER.execute(() -> {
            NativeImage img = null;
            try {
                int[] fin = Develop.finish(base, r.result(), r.spirit(), r.ghost(), seed);
                img = new NativeImage(W, H, false);
                for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) img.setPixelRGBA(x, y, fin[y * W + x]);
                if (file != null) {
                    Files.createDirectories(file.getParent());
                    img.writeToFile(file);
                }
            } catch (Throwable t) {
                Reisatsu.LOGGER.error("[reisatsu] saving a photo failed", t);
            }
            NativeImage done = img;
            mc.execute(() -> {
                if (done != null) Photos.register(id, done);
                PhotoView.eject(id, r);
            });
        });
    }

    // ================================================================================================ instant film
    /** The developing of the film: pure pixel work on ABGR ints (safe off the render thread). */
    static final class Develop {
        private static int[] blur;
        private static int blurW, blurH;
        private static final int[][] GHOST = new int[4][];
        private static final int[][] GHOST_SIZE = new int[4][];
        private static boolean loaded;

        private Develop() {}

        private static synchronized void load() {
            if (loaded) return;
            loaded = true;
            int[] s = new int[2];
            blur = Tex.pixels(Reisatsu.id("textures/gui/photo_blur.png"), s);
            blurW = s[0];
            blurH = s[1];
            for (int i = 1; i <= 3; i++) {
                int[] gs = new int[2];
                GHOST[i] = Tex.pixels(Reisatsu.id("textures/gui/photo_ghost_" + i + ".png"), gs);
                GHOST_SIZE[i] = gs;
            }
        }

        static int[] base(int[] src, int w, int h, boolean flashPost, long seed) {
            Random rnd = new Random(seed);
            int ch = h, cw = Math.round(h * 4.0f / 3.0f);
            if (cw > w) {
                cw = w;
                ch = Math.round(w * 3.0f / 4.0f);
            }
            int x0 = (w - cw) / 2, y0 = (h - ch) / 2;
            float[] r = new float[W * H], g = new float[W * H], b = new float[W * H];
            for (int oy = 0; oy < H; oy++) {
                int sy0 = y0 + oy * ch / H, sy1 = Math.max(sy0 + 1, y0 + (oy + 1) * ch / H);
                for (int ox = 0; ox < W; ox++) {
                    int sx0 = x0 + ox * cw / W, sx1 = Math.max(sx0 + 1, x0 + (ox + 1) * cw / W);
                    float sr = 0, sg = 0, sb = 0;
                    int n = 0;
                    for (int sy = sy0; sy < sy1; sy++) {
                        for (int sx = sx0; sx < sx1; sx++) {
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
            // light leak: a warm glow creeping in from one edge
            int leakEdge = rnd.nextFloat() < 0.65f ? rnd.nextInt(3) : -1;
            float leakStrength = 0.12f + rnd.nextFloat() * 0.3f;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int i = y * W + x;
                    float nx = (x + 0.5f) / W * 2 - 1, ny = (y + 0.5f) / H * 2 - 1;
                    float rr = (float) Math.sqrt(nx * nx * 1.33f + ny * ny);
                    float cr = r[i], cg = g[i], cb = b[i];
                    if (flashPost) {
                        // the flash lights what is near the centre, falling off toward the edges
                        float f = (float) Math.exp(-(nx * nx * 1.2f + ny * ny) / 0.55f);
                        float gamma = 1.0f / (1.0f + 1.5f * f);
                        cr = (float) Math.pow(cr, gamma) * (1.0f + 0.55f * f) + 0.035f * f;
                        cg = (float) Math.pow(cg, gamma) * (1.0f + 0.57f * f) + 0.037f * f;
                        cb = (float) Math.pow(cb, gamma) * (1.0f + 0.62f * f) + 0.042f * f;
                    }
                    // warm shift
                    cr *= 1.07f;
                    cg *= 1.0f;
                    cb *= 0.86f;
                    // gentle S-curve
                    cr = curve(cr);
                    cg = curve(cg);
                    cb = curve(cb);
                    // faded, slightly teal blacks; creamy highlights
                    cr = 0.075f + cr * 0.86f;
                    cg = 0.082f + cg * 0.85f;
                    cb = 0.088f + cb * 0.80f;
                    // vignette
                    float v = 1.0f - 0.48f * (float) Math.pow(Math.min(1.2f, rr), 2.3);
                    cr *= v;
                    cg *= v;
                    cb *= v;
                    if (leakEdge >= 0) {
                        float dist = switch (leakEdge) {
                            case 0 -> (x + 0.5f) / W;
                            case 1 -> 1.0f - (x + 0.5f) / W;
                            default -> (y + 0.5f) / H;
                        };
                        float l = (float) Math.exp(-dist / 0.18f) * leakStrength;
                        cr += l * 1.0f;
                        cg += l * 0.42f;
                        cb += l * 0.12f;
                    }
                    r[i] = cr;
                    g[i] = cg;
                    b[i] = cb;
                }
            }
            r = blur3(r);
            g = blur3(g);
            b = blur3(b);
            int[] out = new int[W * H];
            for (int i = 0; i < out.length; i++) {
                float n = (float) rnd.nextGaussian() * 0.032f;
                out[i] = pack(r[i] + n, g[i] + n * 0.95f, b[i] + n * 1.05f);
            }
            return out;
        }

        private static float curve(float c) {
            c = Math.max(0.0f, Math.min(1.0f, c));
            return c + 0.16f * (c - 0.5f) * (1.0f - Math.abs(2.0f * c - 1.0f));
        }

        private static float[] blur3(float[] a) {
            float[] o = new float[a.length];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    float s = 0;
                    float wsum = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = Math.max(0, Math.min(H - 1, y + dy));
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = Math.max(0, Math.min(W - 1, x + dx));
                            float wt = (dx == 0 && dy == 0) ? 4.0f : (dx == 0 || dy == 0) ? 1.5f : 1.0f;
                            s += a[yy * W + xx] * wt;
                            wsum += wt;
                        }
                    }
                    o[y * W + x] = s / wsum;
                }
            }
            return o;
        }

        static int pack(float r, float g, float b) {
            int ir = Math.max(0, Math.min(255, Math.round(r * 255)));
            int ig = Math.max(0, Math.min(255, Math.round(g * 255)));
            int ib = Math.max(0, Math.min(255, Math.round(b * 255)));
            return 0xFF000000 | (ib << 16) | (ig << 8) | ir;
        }

        /** The verdict on the image: the smear of a spirit that noticed you, a faint face in an empty frame. */
        static int[] finish(int[] base, int result, @Nullable SpiritKind spirit, int ghost, long seed) {
            load();
            int[] px = base.clone();
            if (result == GameData.RESULT_FAILED) {
                // smeared sideways, darker, with a pale face-like blur
                for (int pass = 0; pass < 2; pass++) {
                    int[] o = new int[px.length];
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int sr = 0, sg = 0, sb = 0, n = 0;
                            for (int d = -9; d <= 9; d++) {
                                int c = px[y * W + Math.max(0, Math.min(W - 1, x + d))];
                                sr += c & 0xFF;
                                sg += (c >> 8) & 0xFF;
                                sb += (c >> 16) & 0xFF;
                                n++;
                            }
                            o[y * W + x] = pack(sr / (float) n / 255 * 0.86f, sg / (float) n / 255 * 0.86f, sb / (float) n / 255 * 0.9f);
                        }
                    }
                    px = o;
                }
                if (blur != null) overlay(px, blur, blurW, blurH, 0.9f, false);
                else faceBlob(px, new Random(seed));
            } else if (result == GameData.RESULT_EMPTY && ghost > 0 && ghost <= 3 && GHOST[ghost] != null) {
                overlay(px, GHOST[ghost], GHOST_SIZE[ghost][0], GHOST_SIZE[ghost][1], 0.5f, true);
            }
            return px;
        }

        private static void overlay(int[] px, int[] ov, int ow, int oh, float strength, boolean screen) {
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int c = ov[(y * oh / H) * ow + (x * ow / W)];
                    float a = ((c >>> 24) & 0xFF) / 255.0f * strength;
                    if (a <= 0.003f) continue;
                    int i = y * W + x;
                    int p = px[i];
                    float pr = (p & 0xFF) / 255.0f, pg = ((p >> 8) & 0xFF) / 255.0f, pb = ((p >> 16) & 0xFF) / 255.0f;
                    float or = (c & 0xFF) / 255.0f, og = ((c >> 8) & 0xFF) / 255.0f, ob = ((c >> 16) & 0xFF) / 255.0f;
                    if (screen) {
                        pr = 1 - (1 - pr) * (1 - or * a);
                        pg = 1 - (1 - pg) * (1 - og * a);
                        pb = 1 - (1 - pb) * (1 - ob * a);
                    } else {
                        pr = pr * (1 - a) + or * a;
                        pg = pg * (1 - a) + og * a;
                        pb = pb * (1 - a) + ob * a;
                    }
                    px[i] = pack(pr, pg, pb);
                }
            }
        }

        /** Without the art's blur texture: a pale oval with two dark hollows, smeared. */
        private static void faceBlob(int[] px, Random rnd) {
            float cx = W * (0.4f + rnd.nextFloat() * 0.2f), cy = H * (0.38f + rnd.nextFloat() * 0.15f);
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    float dx = (x - cx) / 46.0f, dy = (y - cy) / 62.0f;
                    float d = dx * dx * 0.6f + dy * dy;
                    float a = (float) Math.exp(-d * 1.6f) * 0.55f;
                    float eyeL = (float) Math.exp(-(Math.pow((x - cx + 14) / 7.0, 2) + Math.pow((y - cy + 8) / 9.0, 2)));
                    float eyeR = (float) Math.exp(-(Math.pow((x - cx - 14) / 7.0, 2) + Math.pow((y - cy + 8) / 9.0, 2)));
                    float mouth = (float) Math.exp(-(Math.pow((x - cx) / 10.0, 2) + Math.pow((y - cy - 26) / 7.0, 2)));
                    int i = y * W + x;
                    int p = px[i];
                    float pr = (p & 0xFF) / 255.0f, pg = ((p >> 8) & 0xFF) / 255.0f, pb = ((p >> 16) & 0xFF) / 255.0f;
                    pr = pr * (1 - a) + 0.82f * a;
                    pg = pg * (1 - a) + 0.84f * a;
                    pb = pb * (1 - a) + 0.86f * a;
                    float dark = Math.min(1.0f, (eyeL + eyeR) * 0.8f + mouth * 0.6f);
                    pr *= 1 - dark * 0.85f;
                    pg *= 1 - dark * 0.85f;
                    pb *= 1 - dark * 0.85f;
                    px[i] = pack(pr, pg, pb);
                }
            }
        }
    }
}
