package com.reisatsu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.reisatsu.Reisatsu;
import com.reisatsu.ReisatsuConfig;
import com.reisatsu.client.sound.LoopSound;
import com.reisatsu.entity.Spirit;
import com.reisatsu.game.Camera;
import com.reisatsu.game.Sight;
import com.reisatsu.item.GameItem;
import com.reisatsu.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Looking through 霊写機 (docs/DESIGN.md §3.1): the optical finder frame, the focus circle (red on a photographable
 * face), のこり N枚, the 霊感計 needle swinging toward spirit presence (with its crackle), scroll zoom (FOV 70 → 30),
 * the shutter (left click) and the flash — the shader's {@code blindness} channel while a pack is active (Iris reads
 * the effect's duration / 20), a white flash of the HUD and a brightened photo without one.
 */
public final class Finder {
    private static final ResourceLocation FRAME = Reisatsu.id("textures/gui/finder.png");
    private static final ResourceLocation METER = Reisatsu.id("textures/gui/finder_meter.png");
    private static final RandomSource RAND = RandomSource.create();
    public static float zoom, zoomShown, zoomPrev;
    private static int cooldown;
    private static int flashTicks = -1;
    private static boolean ownBlindness;
    public static float meter, meterShown;
    public static boolean focusRed;
    private static LoopSound sense;
    private static boolean wasActive;
    private static int hudFlash = -1;

    private Finder() {}

    public static boolean active(Minecraft mc) {
        return mc.player != null && GameItem.is(mc.player.getMainHandItem(), GameItem.Kind.CAMERA_FINDER) && !ClientState.hiding();
    }

    public static void reset() {
        zoom = zoomShown = zoomPrev = 0.0f;
        hudFlash = -1;
    }

    /** Vertical FOV through the finder at this zoom. */
    public static float fov(float partial) {
        return Mth.lerp(Mth.lerp(partial, zoomPrev, zoomShown), 70.0f, 30.0f);
    }

    public static void scroll(double delta) {
        float old = zoom;
        zoom = Mth.clamp(zoom + (delta > 0 ? 0.1f : -0.1f), 0.0f, 1.0f);
        if (old != zoom) ClientPacketHandler.ui(ModSounds.CAMERA_ZOOM.get(), 0.5f, 0.9f + zoom * 0.3f);
    }

    /** The shutter (the attack key in the finder). */
    public static void shutter(Minecraft mc) {
        if (cooldown > 0 || mc.player == null) return;
        int film = GameItem.film(mc.player.getMainHandItem());
        if (film <= 0) {
            ClientPacketHandler.ui(ModSounds.CAMERA_EMPTY.get(), 0.9f, 1.0f);
            cooldown = 8;
            return;
        }
        cooldown = 24;
        PhotoCapture.request(mc, fov(1.0f), zoom);
        flashTicks = 0;
        hudFlash = 0;
        ClientPacketHandler.ui(ModSounds.CAMERA_CHARGE.get(), 0.35f, 1.0f);
    }

    /** The flash right now (1 on the shutter frame, gone after ~0.25 s). */
    public static float flash(float partial) {
        if (flashTicks < 0) return 0.0f;
        return Math.max(0.0f, 1.0f - (flashTicks + partial) / 5.0f);
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (cooldown > 0) cooldown--;
        zoomPrev = zoomShown;
        zoomShown += (zoom - zoomShown) * 0.35f;
        boolean act = active(mc);
        if (act != wasActive) {
            ClientPacketHandler.ui(act ? ModSounds.CAMERA_RAISE.get() : ModSounds.CAMERA_LOWER.get(), 0.7f, 1.0f);
            wasActive = act;
            if (!act) zoom = 0.0f;
        }
        if (hudFlash >= 0 && ++hudFlash > 10) hudFlash = -1;
        // the flash on the shader's blindness channel
        if (flashTicks >= 0) {
            flashTicks++;
            float f = flash(0.0f);
            boolean channel = ShaderCompat.active() && !ReisatsuConfig.flashReduction();
            if (channel) {
                mc.player.removeEffectNoUpdate(MobEffects.BLINDNESS);
                int dur = Math.round(f * 20.0f);
                if (dur > 0) {
                    mc.player.forceAddEffect(new MobEffectInstance(MobEffects.BLINDNESS, dur, 0, false, false, false), null);
                    ownBlindness = true;
                }
            }
            if (f <= 0.0f) {
                flashTicks = -1;
                if (ownBlindness) {
                    mc.player.removeEffectNoUpdate(MobEffects.BLINDNESS);
                    ownBlindness = false;
                }
            }
        }
        // the 霊感計 and the focus
        meter = 0.0f;
        focusRed = false;
        if (act && mc.level != null) {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 look = mc.player.getViewVector(1.0f);
            float half = fov(1.0f) * 0.75f;
            float s = 0.0f;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof Spirit sp) || sp.flag(Spirit.F_BOUND)) continue;
                Vec3 h = Camera.head(sp);
                Vec3 to = h.subtract(eye);
                double d = to.length();
                if (d > 26 || d < 0.1) continue;
                double ang = Math.toDegrees(Math.acos(Mth.clamp(to.scale(1.0 / d).dot(look), -1.0, 1.0)));
                if (ang > half * 1.5) continue;
                s += (float) (Math.pow(1.0 - d / 26.0, 1.4) * (1.0 - ang / (half * 1.5)));
                if (!sp.flag(Spirit.F_PHOTOGENIC) || sp.flag(Spirit.F_HUNTING)) continue;
                double[] pr = Camera.project(eye, mc.player.getYRot(), mc.player.getXRot(), fov(1.0f), h);
                if (pr == null) continue;
                double r = Math.sqrt(Math.pow(pr[0] * Camera.ASPECT, 2) + pr[1] * pr[1]);
                if (r > 0.28 || d > Camera.maxDistance(zoom)) continue;
                if (Camera.faceAngle(sp, eye) > sp.faceAngle()) continue;
                if (!Sight.clear(mc.level, eye, h)) continue;
                focusRed = true;
            }
            meter = Mth.clamp(s, 0.0f, 1.0f);
        }
        float jitter = meter > 0.05f ? (RAND.nextFloat() - 0.5f) * 0.12f * meter : 0.0f;
        meterShown += (meter + jitter - meterShown) * 0.2f;
        // the crackle of the meter
        if (act) {
            if (sense == null || !mc.getSoundManager().isActive(sense)) {
                sense = new LoopSound(ModSounds.CAMERA_SENSE.get(), SoundSource.PLAYERS, 0.15f);
                mc.getSoundManager().play(sense);
            }
            sense.target = Mth.clamp(meterShown, 0.0f, 1.0f) * 0.75f;
        } else if (sense != null) {
            sense.target = 0.0f;
            if (sense.rawVolume() <= 0.002f) {
                sense.finish();
                sense = null;
            }
        }
    }

    // ================================================================================================ the view
    public static void render(GuiGraphics g, Font font, int w, int h, float partial) {
        Minecraft mc = Minecraft.getInstance();
        boolean shaders = ShaderCompat.active();
        float t = ClientState.clientTicks + partial;
        // without a shader pack: a cool, desaturated, grainy look (the pack draws its own)
        if (!shaders) {
            g.fill(0, 0, w, h, HorrorOverlay.argb(0.20f, 0x1C2C3C));
            HorrorOverlay.grain(g, w, h, 0.22f);
        }
        // the photo window (4:3, full height) and the finder frame
        int ph = (int) (h * 0.92f), pw = (int) (ph * 4.0f / 3.0f);
        if (pw > w * 0.94f) {
            pw = (int) (w * 0.94f);
            ph = (int) (pw * 3.0f / 4.0f);
        }
        int px = (w - pw) / 2, py = (h - ph) / 2;
        if (Tex.has(FRAME)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(FRAME, 0, 0, w, h, 0, 0, 512, 288, 512, 288);
            RenderSystem.disableBlend();
        } else {
            // a stand-in frame: dark mask outside the window, bright corner lines
            int mask = 0xD8050505;
            g.fill(0, 0, w, py, mask);
            g.fill(0, py + ph, w, h, mask);
            g.fill(0, py, px, py + ph, mask);
            g.fill(px + pw, py, w, py + ph, mask);
            int col = 0xC0E8EEF0, len = 22;
            int[][] corners = {{px + 6, py + 6, 1, 1}, {px + pw - 7, py + 6, -1, 1}, {px + 6, py + ph - 7, 1, -1}, {px + pw - 7, py + ph - 7, -1, -1}};
            for (int[] c : corners) {
                g.fill(Math.min(c[0], c[0] + c[2] * len), c[1], Math.max(c[0], c[0] + c[2] * len) + 1, c[1] + 1, col);
                g.fill(c[0], Math.min(c[1], c[1] + c[3] * len), c[0] + 1, Math.max(c[1], c[1] + c[3] * len) + 1, col);
            }
            // parallax marks
            g.fill(w / 2 - 30, py + ph / 10, w / 2 - 18, py + ph / 10 + 1, 0x90E8EEF0);
            g.fill(w / 2 + 18, py + ph / 10, w / 2 + 30, py + ph / 10 + 1, 0x90E8EEF0);
        }
        // the 80 % frame (where a face must be)
        int fx = (int) (pw * 0.1f), fy = (int) (ph * 0.1f);
        int fc = 0x38E8EEF0;
        g.fill(px + fx, py + fy, px + fx + 10, py + fy + 1, fc);
        g.fill(px + fx, py + fy, px + fx + 1, py + fy + 10, fc);
        g.fill(px + pw - fx - 10, py + fy, px + pw - fx, py + fy + 1, fc);
        g.fill(px + pw - fx - 1, py + fy, px + pw - fx, py + fy + 10, fc);
        g.fill(px + fx, py + ph - fy - 1, px + fx + 10, py + ph - fy, fc);
        g.fill(px + fx, py + ph - fy - 10, px + fx + 1, py + ph - fy, fc);
        g.fill(px + pw - fx - 10, py + ph - fy - 1, px + pw - fx, py + ph - fy, fc);
        g.fill(px + pw - fx - 1, py + ph - fy - 10, px + pw - fx, py + ph - fy, fc);
        // the focus circle
        int r = (int) (ph * 0.14f);
        int col = focusRed ? 0xE8E02818 : 0x90E8EEF0;
        ring(g, w / 2, h / 2, r, col);
        if (focusRed) ring(g, w / 2, h / 2, r - 1, 0x80E02818);
        g.fill(w / 2 - 3, h / 2, w / 2 + 4, h / 2 + 1, 0x70E8EEF0);
        g.fill(w / 2, h / 2 - 3, w / 2 + 1, h / 2 + 4, 0x70E8EEF0);
        // film left
        String left = I18n.get("hud.reisatsu.film_left", ClientState.film);
        int lc = ClientState.film <= 0 ? 0xFFE04030 : 0xE0E8F0E0;
        g.drawString(font, left, px + pw - fx - font.width(left), py + ph - fy + 4, lc, false);
        // zoom scale
        int zx = px + pw - fx + 8, zy0 = h / 2 - 40, zy1 = h / 2 + 40;
        g.fill(zx, zy0, zx + 1, zy1, 0x60E8EEF0);
        int zy = (int) Mth.lerp(Mth.lerp(partial, zoomPrev, zoomShown), zy1, zy0);
        g.fill(zx - 3, zy, zx + 4, zy + 1, 0xD0E8EEF0);
        g.drawString(font, String.format(java.util.Locale.ROOT, "%.1fx", 70.0f / fov(partial)), zx + 6, zy - 4, 0x90E8EEF0, false);
        // the 霊感計
        meterDial(g, font, px + fx, py + ph - fy - 50, t);
        if (mc.player != null && ClientState.clientTicks % 40 < 30 && ClientState.film <= 0) {
            g.drawCenteredString(font, I18n.get("hud.reisatsu.no_film"), w / 2, py + ph - fy - 14, 0xFFE04030);
        }
        // the flash on the HUD (and the only flash without a shader pack)
        if (hudFlash >= 0) {
            float k = hudFlash + partial;
            float a = Math.max(0.0f, 1.0f - k / 8.0f) * (shaders ? 0.3f : 0.92f) * (ReisatsuConfig.flashReduction() ? 0.25f : 1.0f);
            g.fill(0, 0, w, h, HorrorOverlay.argb(a, 0xF4F8FF));
        }
    }

    private static void meterDial(GuiGraphics g, Font font, int x, int y, float t) {
        int dw = 96, dh = 48;
        if (Tex.has(METER)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(METER, x, y, dw, dh, 0, 0, 128, 64, 128, 64);
            RenderSystem.disableBlend();
        } else {
            g.fill(x, y, x + dw, y + dh, 0xB0101418);
            g.drawString(font, I18n.get("hud.reisatsu.meter"), x + 4, y + 4, 0xA0D0D8D0, false);
            for (int i = 0; i <= 8; i++) {
                double a = Math.toRadians(-60 + i * 15);
                int tx = x + dw / 2 + (int) (Math.sin(a) * 36), ty = y + dh - 6 - (int) (Math.cos(a) * 36);
                g.fill(tx, ty, tx + 1, ty + 2, i >= 6 ? 0xC0E03020 : 0xA0D0D8D0);
            }
        }
        float m = Mth.clamp(meterShown, 0.0f, 1.0f);
        float angle = -60.0f + 120.0f * m;
        g.pose().pushPose();
        g.pose().translate(x + dw / 2.0f, y + dh - 6.0f, 0.0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(angle));
        g.fill(0, -34, 1, 0, m > 0.7f ? 0xF0E83020 : 0xF0E8E0D0);
        g.pose().popPose();
    }

    /** A thin ring of small squares. */
    static void ring(GuiGraphics g, int cx, int cy, int r, int col) {
        int n = Math.max(24, r * 2);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, col);
        }
    }
}
