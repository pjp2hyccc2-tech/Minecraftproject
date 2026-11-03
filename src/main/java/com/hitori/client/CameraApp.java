package com.hitori.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.hitori.Hitori;
import com.hitori.HitoriConfig;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.OniDoll;
import com.hitori.entity.Woman;
import com.hitori.game.Clock;
import com.hitori.game.GameManager;
import com.hitori.game.Watch;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The camera app (docs/DESIGN.md §3.1) on the laptop (large) or the phone (small): the view becomes the selected fixed
 * camera ({@code Minecraft.setCameraEntity}; your body stays where it is), with the CCTV overlay — REC ●, CAM n and
 * its room, the live timestamp, the battery, a slow scanline, noise, the IR look in the dark. Keys 1–4 / A–D switch
 * cameras, ESC or right-click closes. The listener follows the camera (vanilla does that), the acoustics add the
 * microphone filter ({@link ClientState#cameraView()}).
 * <p>
 * The shader's {@code nightVision} channel: a hidden night vision effect on the camera entity being viewed (shader
 * loaders read it from the camera entity; without a pack the player gets it too, for vanilla's brightening, in the dark).
 * Feeds glitch: noise when the doll or the woman is in view, a freeze (the last frame held while time runs on), a
 * blackout, the timestamp jumping. The doll seen through a camera is drawn in stop-motion ({@link #stopFrame()}).
 */
public final class CameraApp {
    private static final ResourceLocation CCTV = Hitori.id("textures/gui/cctv.png");
    private static final ResourceLocation LAPTOP = Hitori.id("textures/gui/laptop_frame.png");
    private static final ResourceLocation PHONE = Hitori.id("textures/gui/phone_frame.png");
    private static final ResourceLocation FREEZE = Hitori.id("feed_freeze");
    private static final RandomSource RAND = RandomSource.create();

    private static boolean open;
    private static int device;
    private static int number;
    private static int lastNumber;
    private static int openTicks;
    private static int switchTicks = -1;
    // glitches
    private static int freezeTicks = -1;
    private static boolean freezeWanted;
    private static boolean freezeReady;
    @Nullable private static DynamicTexture freezeTex;
    private static int blackoutTicks = -1;
    private static int noiseTicks = -1;
    private static float noise;
    private static int stampJumpTicks = -1;
    private static float stampOffset;
    // the doll's stop-motion
    private static int stopFrame;
    private static int stopNext;
    private static boolean dollInView, womanInView;
    private static int dollMovingTicks;
    // night vision
    @Nullable private static FixedCamera nvCamera;
    private static boolean playerNv;
    /** The camera feed is dark (IR monochrome). */
    private static boolean ir;

    private CameraApp() {}

    public static boolean isOpen() {
        return open;
    }

    /** The app is open and the view is a camera. */
    public static boolean viewing() {
        return open && Minecraft.getInstance().getCameraEntity() instanceof FixedCamera;
    }

    public static int number() {
        return number;
    }

    public static int device() {
        return device;
    }

    public static boolean ir() {
        return ir;
    }

    public static List<FixedCamera> cameras(Minecraft mc) {
        List<FixedCamera> l = new ArrayList<>();
        if (mc.level == null) return l;
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof FixedCamera c && !c.clientOnly && c.isAlive() && c.number() > 0) l.add(c);
        l.sort(Comparator.comparingInt(FixedCamera::number));
        return l;
    }

    @Nullable
    private static FixedCamera camera(Minecraft mc, int n) {
        for (FixedCamera c : cameras(mc)) if (c.number() == n) return c;
        return null;
    }

    // ================================================================================================ open / close / switch
    public static void open(int dev) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientState.inGame() || ClientState.locked() || open) return;
        if (Peek.active()) Peek.stop(mc);
        List<FixedCamera> cams = cameras(mc);
        if (cams.isEmpty()) {
            ClientPacketHandler.thought("thought.hitori.no_cameras");
            return;
        }
        device = dev;
        open = true;
        openTicks = 0;
        FixedCamera cam = camera(mc, lastNumber);
        if (cam == null) cam = cams.get(0);
        number = cam.number();
        show(mc, cam);
        switchTicks = 0;
        ClientPacketHandler.ui(ModSounds.CAMERA_APP_OPEN.get(), 0.6f, 1.0f);
        if (dev == 0) ClientPacketHandler.ui(ModSounds.LAPTOP_OPEN.get(), 0.5f, 1.0f);
        send();
    }

    public static void close() {
        if (!open) return;
        Minecraft mc = Minecraft.getInstance();
        open = false;
        lastNumber = number;
        if (mc.player != null && mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
        removeNv(mc);
        freezeTicks = blackoutTicks = noiseTicks = stampJumpTicks = -1;
        freezeWanted = false;
        ClientPacketHandler.ui(ModSounds.CAMERA_APP_CLOSE.get(), 0.5f, 1.0f);
        ModNetwork.sendToServer(new C2S.Action(C2S.APP, 0));
    }

    /** Closed by the server or a cinematic: no sound. */
    public static void forceClose() {
        if (!open) return;
        Minecraft mc = Minecraft.getInstance();
        open = false;
        lastNumber = number;
        if (mc.player != null && mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
        removeNv(mc);
    }

    public static void select(int n) {
        Minecraft mc = Minecraft.getInstance();
        if (!open || n == number) return;
        FixedCamera cam = camera(mc, n);
        if (cam == null) return;
        number = n;
        show(mc, cam);
        switchTicks = 0;
        ClientPacketHandler.ui(ModSounds.CAMERA_SWITCH.get(), 0.6f, 0.95f + RAND.nextFloat() * 0.1f);
        send();
    }

    public static void cycle(int dir) {
        Minecraft mc = Minecraft.getInstance();
        List<FixedCamera> cams = cameras(mc);
        if (cams.isEmpty()) return;
        int i = 0;
        for (int k = 0; k < cams.size(); k++) if (cams.get(k).number() == number) i = k;
        i = Math.floorMod(i + dir, cams.size());
        select(cams.get(i).number());
    }

    private static void send() {
        ModNetwork.sendToServer(new C2S.Action(C2S.APP, (number & 0xFF) | (device << 8)));
    }

    private static void show(Minecraft mc, FixedCamera cam) {
        removeNv(mc);
        mc.setCameraEntity(cam);
        nvCamera = cam;
        cam.forceAddEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100000, 0, false, false, false), null);
    }

    private static void removeNv(Minecraft mc) {
        if (nvCamera != null) {
            nvCamera.removeEffectNoUpdate(MobEffects.NIGHT_VISION);
            nvCamera = null;
        }
        if (playerNv && mc.player != null) {
            mc.player.removeEffectNoUpdate(MobEffects.NIGHT_VISION);
            playerNv = false;
        }
    }

    // ================================================================================================ glitches
    /** 0 noise burst, 1 freeze, 2 blackout, 3 timestamp jump. */
    public static void glitch(int kind) {
        if (!open) return;
        boolean calm = HitoriConfig.flashReduction();
        switch (kind) {
            case 0 -> {
                noiseTicks = 0;
                noise = 1.0f;
                if (!calm) ClientPacketHandler.ui(ModSounds.CAMERA_GLITCH.get(), 0.5f, 0.9f + RAND.nextFloat() * 0.2f);
            }
            case 1 -> {
                freezeWanted = true;
                freezeTicks = -1;
            }
            case 2 -> {
                blackoutTicks = 0;
                ClientPacketHandler.ui(ModSounds.CAMERA_GLITCH.get(), 0.6f, 0.7f);
            }
            case 3 -> {
                stampJumpTicks = 0;
                stampOffset = RAND.nextBoolean() ? 60 * (5 + RAND.nextInt(40)) : -60 * (3 + RAND.nextInt(20));
            }
            default -> {
            }
        }
    }

    /** The doll's frame clock while watched through a camera: it changes 4–6 times a second, unevenly. */
    public static int stopFrame() {
        return stopFrame;
    }

    // ================================================================================================ tick
    public static void tick(Minecraft mc) {
        if (!open) {
            if (nvCamera != null || playerNv) removeNv(mc);
            return;
        }
        if (mc.player == null || !ClientState.inGame() || ClientState.flag(GameManager.F_CAUGHT) || ClientState.flag(GameManager.F_ENDING)
                || ClientState.jumpscareTicks >= 0) {
            forceClose();
            return;
        }
        FixedCamera cam = camera(mc, number);
        if (cam == null) {
            List<FixedCamera> cams = cameras(mc);
            if (cams.isEmpty()) {
                close();
                return;
            }
            number = cams.get(0).number();
            cam = cams.get(0);
            show(mc, cam);
            send();
        }
        if (mc.getCameraEntity() != cam) show(mc, cam);
        if (!cam.hasEffect(MobEffects.NIGHT_VISION)) cam.forceAddEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100000, 0, false, false, false), null);
        openTicks++;
        if (switchTicks >= 0 && ++switchTicks > 6) switchTicks = -1;
        // IR in the dark; without a shader pack the player's night vision brightens the world
        int light = mc.level.getBrightness(LightLayer.BLOCK, BlockPos.containing(cam.getEyePosition()));
        ir = light < 7;
        boolean wantPlayerNv = ir && !ShaderCompat.active();
        if (wantPlayerNv && !playerNv) {
            mc.player.forceAddEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100000, 0, false, false, false), null);
            playerNv = true;
        } else if (!wantPlayerNv && playerNv) {
            mc.player.removeEffectNoUpdate(MobEffects.NIGHT_VISION);
            playerNv = false;
        }
        // the doll's stop-motion clock
        if (--stopNext <= 0) {
            stopFrame++;
            stopNext = 3 + RAND.nextInt(4);
        }
        // what is in the feed
        dollInView = false;
        womanInView = false;
        boolean dollMoving = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof OniDoll d) {
                if (Watch.sees(mc.level, cam.getEyePosition(), cam.getYRot(), cam.getXRot(), Math.tan(Math.toRadians(35)), 1.6, 18.0,
                        d.position().add(0.0, 0.3, 0.0))) {
                    dollInView = true;
                    dollMoving = d.flag(OniDoll.F_MOVING);
                }
            } else if (e instanceof Woman w && w.haunts() == number && w.pose() != Woman.GONE) {
                womanInView = true;
            }
        }
        dollMovingTicks = dollMoving ? dollMovingTicks + 1 : 0;
        boolean calm = HitoriConfig.flashReduction();
        float want = (dollInView ? (dollMoving ? 0.35f : 0.18f) : 0.0f) + (womanInView ? 0.3f : 0.0f);
        noise += (want - noise) * 0.1f;
        if (!calm && dollMoving && RAND.nextInt(50) == 0) glitch(0);
        if (!calm && womanInView && RAND.nextInt(70) == 0) glitch(0);
        if (noiseTicks >= 0 && ++noiseTicks > 10) noiseTicks = -1;
        if (blackoutTicks >= 0 && ++blackoutTicks > 30) blackoutTicks = -1;
        if (stampJumpTicks >= 0 && ++stampJumpTicks > 50) stampJumpTicks = -1;
        if (freezeTicks >= 0 && ++freezeTicks > 40) {
            freezeTicks = -1;
            freezeReady = false;
        }
        // now and then the feed itself misbehaves (more as the night goes on)
        if (!calm && RAND.nextInt(1800) == 0) glitch(RAND.nextBoolean() ? 1 : 3);
        FrameCapture.feedTick(mc, number, openTicks);
    }

    /** Before the HUD is drawn: grab the frame for a freeze. */
    public static void onRenderGuiPre(Minecraft mc) {
        if (!freezeWanted || !viewing()) return;
        freezeWanted = false;
        try {
            NativeImage img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            freezeTex = new DynamicTexture(img);
            mc.getTextureManager().register(FREEZE, freezeTex);
            freezeReady = true;
            freezeTicks = 0;
        } catch (Throwable t) {
            Hitori.LOGGER.warn("[hitori] feed freeze failed: {}", t.toString());
        }
    }

    // ================================================================================================ the overlay
    /** The device frame and the CCTV look over the camera's view (the HUD's place while the app is open). */
    public static void render(GuiGraphics g, Font font, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!open) return;
        float t = ClientState.clientTicks + partial;
        // the feed's window inside the device
        int x0, y0, x1, y1;
        if (device == 0) {
            int bx = w / 22, top = h / 14, bottom = h / 9;
            x0 = bx;
            y0 = top;
            x1 = w - bx;
            y1 = h - bottom;
        } else {
            int fw = (int) (w * 0.58f), fh = fw * 9 / 16;
            x0 = (w - fw) / 2;
            y0 = (h - fh) / 2 - h / 20;
            x1 = x0 + fw;
            y1 = y0 + fh;
        }
        int fw = x1 - x0, fh = y1 - y0;
        // frozen frame / blackout / switching static
        if (freezeTicks >= 0 && freezeReady && freezeTex != null) {
            RenderSystem.enableBlend();
            g.blit(FREEZE, x0, y0, fw, fh, x0 * (float) mc.getWindow().getWidth() / w, y0 * (float) mc.getWindow().getHeight() / h,
                    (int) (fw * (float) mc.getWindow().getWidth() / w), (int) (fh * (float) mc.getWindow().getHeight() / h),
                    mc.getWindow().getWidth(), mc.getWindow().getHeight());
            RenderSystem.disableBlend();
        }
        if (ir && !ShaderCompat.active()) {
            // a monochrome IR wash (the shader pack does it properly)
            g.fill(x0, y0, x1, y1, HorrorOverlay.argb(0.42f, 0x1C2A22));
            g.fill(x0, y0, x1, y1, HorrorOverlay.argb(0.10f, 0x9CB8A4));
        }
        // scanline, interlace, grain
        int scan = y0 + (int) ((t * 1.3f) % Math.max(1, fh));
        g.fill(x0, scan, x1, scan + 2, HorrorOverlay.argb(0.10f, 0xE0F0E0));
        for (int y = y0; y < y1; y += 3) g.fill(x0, y, x1, y + 1, HorrorOverlay.argb(0.07f, 0x000000));
        float grain = 0.08f + noise * 0.5f + (noiseTicks >= 0 ? 0.5f : 0.0f) + (switchTicks >= 0 ? 1.0f : 0.0f);
        HorrorOverlay.grainIn(g, x0, y0, x1, y1, Math.min(0.9f, grain));
        if (noiseTicks >= 0 || switchTicks >= 0 || (noise > 0.15f && RAND.nextFloat() < noise * 0.4f)) {
            int bars = switchTicks >= 0 ? 14 : 2 + RAND.nextInt(5);
            for (int i = 0; i < bars; i++) {
                int y = y0 + RAND.nextInt(Math.max(1, fh));
                int bh = 1 + RAND.nextInt(switchTicks >= 0 ? 12 : 5);
                g.fill(x0, y, x1, Math.min(y1, y + bh), RAND.nextBoolean() ? 0xA0000000 : 0x50E8F0E8);
            }
        }
        if (switchTicks >= 0 && switchTicks < 3) g.fill(x0, y0, x1, y1, 0xD0101410);
        if (blackoutTicks >= 0) {
            g.fill(x0, y0, x1, y1, 0xFF000000);
            String ns = I18n.get("hud.hitori.no_signal");
            HorrorOverlay.centered(g, font, ns, (x0 + x1) / 2, (y0 + y1) / 2 - 4, 0xFFC8C8C8, 1.4f);
        }
        // the CCTV frame: REC, CAM n + room, timestamp, battery
        if (Tex.has(CCTV)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(CCTV, x0, y0, fw, fh, 0, 0, 256, 144, 256, 144);
            RenderSystem.disableBlend();
        } else {
            corners(g, x0, y0, x1, y1);
        }
        int pad = Math.max(6, fw / 40);
        boolean recOn = ((int) (t / 12)) % 2 == 0;
        if (recOn) g.fill(x0 + pad, y0 + pad + 1, x0 + pad + 6, y0 + pad + 7, 0xFFE02020);
        g.drawString(font, "REC", x0 + pad + 9, y0 + pad, 0xFFF0F0F0, true);
        FixedCamera cam = camera(mc, number);
        String room = cam == null ? "" : I18n.get("room.hitori." + roomOf(cam.position()));
        g.drawString(font, "CAM" + number + "  " + room, x0 + pad, y0 + pad + 12, 0xFFF0F0F0, true);
        if (ir) g.drawString(font, "IR", x0 + pad, y0 + pad + 24, 0xFFB8E0B8, true);
        if (cam != null && cam.fallen()) g.drawString(font, "!", x0 + pad + 16, y0 + pad + 24, 0xFFE0C040, true);
        double clock = ClientState.clock + (stampJumpTicks >= 0 ? stampOffset : 0.0f);
        String stamp = Clock.stamp(clock);
        int sw = font.width(stamp);
        g.drawString(font, stamp, x1 - pad - sw, y1 - pad - 9, stampJumpTicks >= 0 ? 0xFFE05050 : 0xFFF0F0F0, true);
        int battery = device == 0 ? Mth.clamp(78 - (int) ((ClientState.clock - Clock.START) / 300), 9, 99)
                : Mth.clamp(41 - (int) ((ClientState.clock - Clock.START) / 420), 4, 99);
        battery(g, font, x1 - pad - 30, y0 + pad, battery);
        // the other cameras: little tabs
        List<FixedCamera> cams = cameras(mc);
        int tx = x0 + pad;
        for (FixedCamera c : cams) {
            boolean sel = c.number() == number;
            g.fill(tx, y1 - pad - 11, tx + 14, y1 - pad, sel ? 0xC0E0E0E0 : 0x60202020);
            g.drawString(font, String.valueOf(c.number()), tx + 4, y1 - pad - 9, sel ? 0xFF101010 : 0xFFC0C0C0, false);
            tx += 17;
        }
        if (switchTicks >= 0) HorrorOverlay.centered(g, font, "CAM" + number, (x0 + x1) / 2, (y0 + y1) / 2 - 8, 0xFFF0F0F0, 2.0f);
        // the device around the feed
        if (device == 0) laptopFrame(g, font, w, h, x0, y0, x1, y1, room);
        else phoneFrame(g, font, w, h, x0, y0, x1, y1);
        String hint = I18n.get("hud.hitori.app_keys");
        g.drawString(font, hint, (w - font.width(hint)) / 2, h - 11, 0x90B0B0B0, false);
    }

    static String roomOf(Vec3 at) {
        Layout.Room r = Layout.roomAt(BlockPos.containing(at.x, at.y + 0.2, at.z));
        return r == null ? "outside" : r.key();
    }

    private static void corners(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int L = Math.max(10, (x1 - x0) / 18), c = 0xD0E8E8E8;
        int m = 4;
        g.fill(x0 + m, y0 + m, x0 + m + L, y0 + m + 1, c);
        g.fill(x0 + m, y0 + m, x0 + m + 1, y0 + m + L, c);
        g.fill(x1 - m - L, y0 + m, x1 - m, y0 + m + 1, c);
        g.fill(x1 - m - 1, y0 + m, x1 - m, y0 + m + L, c);
        g.fill(x0 + m, y1 - m - 1, x0 + m + L, y1 - m, c);
        g.fill(x0 + m, y1 - m - L, x0 + m + 1, y1 - m, c);
        g.fill(x1 - m - L, y1 - m - 1, x1 - m, y1 - m, c);
        g.fill(x1 - m - 1, y1 - m - L, x1 - m, y1 - m, c);
        int cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
        g.fill(cx - 5, cy, cx + 6, cy + 1, 0x60E8E8E8);
        g.fill(cx, cy - 5, cx + 1, cy + 6, 0x60E8E8E8);
    }

    private static void battery(GuiGraphics g, Font font, int x, int y, int pct) {
        int col = pct <= 15 ? 0xFFE04030 : 0xFFF0F0F0;
        g.fill(x, y, x + 18, y + 1, col);
        g.fill(x, y + 8, x + 18, y + 9, col);
        g.fill(x, y, x + 1, y + 9, col);
        g.fill(x + 17, y, x + 18, y + 9, col);
        g.fill(x + 18, y + 3, x + 20, y + 6, col);
        g.fill(x + 2, y + 2, x + 2 + Math.max(1, 14 * pct / 100), y + 7, col);
        g.drawString(font, pct + "%", x - font.width(pct + "%") - 3, y, col, true);
    }

    private static void laptopFrame(GuiGraphics g, Font font, int w, int h, int x0, int y0, int x1, int y1, String room) {
        if (Tex.has(LAPTOP)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(LAPTOP, 0, 0, w, h, 0, 0, 256, 160, 256, 160);
            RenderSystem.disableBlend();
            return;
        }
        int bezel = 0xFF16171A;
        g.fill(0, 0, w, y0 - 12, bezel);
        g.fill(0, y1 + 14, w, h, bezel);
        g.fill(0, y0 - 12, x0, y1 + 14, bezel);
        g.fill(x1, y0 - 12, w, y1 + 14, bezel);
        // the app window's title bar (an old OS) and the taskbar
        g.fill(x0, y0 - 12, x1, y0, 0xFF2B4A82);
        g.drawString(font, "CamView  -  CAM" + number + "  " + room, x0 + 4, y0 - 10, 0xFFF0F0F0, false);
        g.fill(x1 - 11, y0 - 10, x1 - 3, y0 - 2, 0xFFC04030);
        g.fill(x0, y1, x1, y1 + 14, 0xFFC0C0C0);
        g.fill(x0, y1, x1, y1 + 1, 0xFFF0F0F0);
        g.fill(x0 + 2, y1 + 2, x0 + 40, y1 + 12, 0xFF3A7A3A);
        g.drawString(font, "start", x0 + 7, y1 + 4, 0xFFF0F0F0, false);
        String hhmm = Clock.hhmm(ClientState.clock);
        g.drawString(font, hhmm, x1 - font.width(hhmm) - 5, y1 + 4, 0xFF202020, false);
        // the camera LED of the laptop itself
        g.fill(w / 2 - 1, y0 - 20, w / 2 + 1, y0 - 18, 0xFF303030);
    }

    private static void phoneFrame(GuiGraphics g, Font font, int w, int h, int x0, int y0, int x1, int y1) {
        // the closet around the phone: near black
        int dark = 0xF4050506;
        g.fill(0, 0, w, y0 - 10, dark);
        g.fill(0, y1 + 10, w, h, dark);
        g.fill(0, y0 - 10, x0 - 14, y1 + 10, dark);
        g.fill(x1 + 14, y0 - 10, w, y1 + 10, dark);
        if (Tex.has(PHONE)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(PHONE, x0 - 14, y0 - 10, (x1 - x0) + 28, (y1 - y0) + 20, 0, 0, 256, 128, 256, 128);
            RenderSystem.disableBlend();
            return;
        }
        int body = 0xFF101114;
        g.fill(x0 - 14, y0 - 10, x1 + 14, y0, body);
        g.fill(x0 - 14, y1, x1 + 14, y1 + 10, body);
        g.fill(x0 - 14, y0, x0, y1, body);
        g.fill(x1, y0, x1 + 14, y1, body);
        g.fill(x0 - 9, (y0 + y1) / 2 - 1, x0 - 6, (y0 + y1) / 2 + 2, 0xFF2A2A2E);
    }

    public static void reset() {
        forceClose();
        lastNumber = 0;
    }
}
