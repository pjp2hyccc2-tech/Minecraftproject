package com.sounan.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sounan.Sounan;
import com.sounan.SounanConfig;
import com.sounan.game.GameManager;
import com.sounan.item.GameItem;
import com.sounan.registry.ModDimensions;
import com.sounan.world.TerrainData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** The screen: atmosphere without shaders, the camera viewfinder, the HUD, and every cinematic overlay. */
public final class HorrorOverlay {
    private static final ResourceLocation VIGNETTE = Sounan.id("textures/gui/vignette.png");
    private static final ResourceLocation GRAIN = Sounan.id("textures/gui/grain.png");
    private static final ResourceLocation HANDS = Sounan.id("textures/gui/hands.png");
    private static final ResourceLocation FACES = Sounan.id("textures/gui/faces_overlay.png");
    private static final ResourceLocation[] SCARES = {
            Sounan.id("textures/gui/jumpscare_nozoki.png"), Sounan.id("textures/gui/jumpscare_yobigoe.png"),
            Sounan.id("textures/gui/jumpscare_ushiro.png"), Sounan.id("textures/gui/jumpscare_koyanushi.png"),
            Sounan.id("textures/gui/jumpscare_oyamasama.png")};
    private static final RandomSource RAND = RandomSource.create();

    private HorrorOverlay() {}

    private static boolean active(Minecraft mc) {
        return mc.level != null && mc.player != null && ModDimensions.isMountain(mc.level);
    }

    static void image(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, float r, float gr, float b, float a) {
        if (a <= 0.003f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(r, gr, b, Mth.clamp(a, 0.0f, 1.0f));
        g.blit(tex, x, y, w, h, 0, 0, 256, 256, 256, 256);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    static boolean holding(Minecraft mc, GameItem.Kind kind) {
        return mc.player != null && (GameItem.kindOf(mc.player.getMainHandItem()) == kind || GameItem.kindOf(mc.player.getOffhandItem()) == kind);
    }

    static void centered(GuiGraphics g, Font font, String text, int cx, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }

    // ================================================================== below the HUD: the night itself
    public static void renderWorld(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || mc.options.hideGui) return;
        float strength = (float) SounanConfig.screenEffects();
        boolean calm = SounanConfig.flashReduction();
        boolean shaders = ShaderCompat.active();
        float shaderMul = shaders ? 0.25f : 1.0f;
        float fear = Mth.lerp(partial, ClientState.fearPrev, ClientState.fearShown) / 100.0f;
        float danger = Mth.lerp(partial, ClientState.dangerPrev, ClientState.dangerShown);
        float t = ClientState.clientTicks + partial;
        boolean camera = holding(mc, GameItem.Kind.PHONE_CAMERA);

        // heartbeat-pulsed vignette and tunnel vision
        float bps = ClientState.bpm() / 60.0f;
        float beat = (float) Math.pow(Math.max(0.0, Math.sin(t / 20.0f * bps * Math.PI)), 12.0);
        float vignette = (0.32f + 0.55f * fear + 0.2f * danger + (fear > 0.45f && !calm ? beat * 0.16f * fear : 0.0f)) * strength;
        image(g, VIGNETTE, 0, 0, w, h, 0.0f, 0.0f, 0.0f, vignette * shaderMul);
        if (fear > 0.6f && !shaders) {
            int inset = (int) ((fear - 0.6f) * w * 0.25f);
            image(g, VIGNETTE, -inset, -inset, w + inset * 2, h + inset * 2, 0.0f, 0.0f, 0.0f, (fear - 0.6f) * 1.2f * strength);
        }
        // film grain (the shader pack has its own)
        float grain = (0.06f + 0.18f * fear + 0.1f * danger + (camera ? 0.25f : 0.0f)) * strength * (shaders ? 0.0f : 1.0f);
        if (grain > 0.01f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0f, 1.0f, 1.0f, Mth.clamp(grain, 0.0f, 0.65f));
            g.blit(GRAIN, 0, 0, w, h, RAND.nextInt(128), RAND.nextInt(128), 128, 128, 256, 256);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
        }
        // faces in the dark at the edge of vision (barely there)
        if (fear > 0.62f && !shaders && !calm) {
            float a = (fear - 0.62f) * 0.45f * (0.5f + 0.5f * Mth.sin(t * 0.021f)) * strength;
            image(g, FACES, 0, 0, w, h, 0.5f, 0.5f, 0.55f, a);
        }
        // danger: blood-dark pulse and reaching hands
        if (danger > 0.3f) {
            float a = (danger - 0.3f) * 0.55f * (calm ? 0.6f : 0.6f + beat * 0.4f) * strength * (shaders ? 0.5f : 1.0f);
            image(g, VIGNETTE, 0, 0, w, h, 0.45f, 0.0f, 0.02f, a);
        }
        if (danger > 0.88f) {
            float a = (danger - 0.88f) * 5.0f;
            int off = (int) ((1.0f - Math.min(1.0f, a)) * 60) + w / 10;
            image(g, HANDS, -off, -off / 2, w + off * 2, h + off, 0.5f, 0.5f, 0.55f, Math.min(0.3f, a * 0.3f));
        }
        // the night-vision camera without a shader pack: green, noisy, scanlines
        if (camera && !shaders) {
            g.fill(0, 0, w, h, argb(0.28f, 0x2E6B2A));
            for (int y = (int) (t * 0.7f) % 3; y < h; y += 3) g.fill(0, y, w, y + 1, 0x18000000);
            image(g, VIGNETTE, 0, 0, w, h, 0.0f, 0.0f, 0.0f, 0.75f);
        }
        if (holding(mc, GameItem.Kind.FLARE_LIT) && !shaders) {
            g.fill(0, 0, w, h, argb(0.10f + 0.04f * Mth.sin(t * 0.9f), 0xB01808));
        }
        // glitches when terror peaks
        if (!calm && ((fear > 0.88f && RAND.nextFloat() < (fear - 0.88f) * 0.8f) || ClientState.glitchTicks >= 0)) {
            int bars = 1 + RAND.nextInt(ClientState.glitchTicks >= 0 ? 6 : 3);
            for (int i = 0; i < bars; i++) {
                int y = RAND.nextInt(h);
                int bh = 1 + RAND.nextInt(6);
                g.fill(0, y, w, y + bh, RAND.nextBoolean() ? 0x90000000 : 0x38FFFFFF);
            }
        }
        // at the edge of panic the brain fills the dark with faces: two frames, then nothing
        if (!calm && SounanConfig.jumpscares() && fear > 0.9f && ClientState.subliminal <= 0 && RAND.nextFloat() < 0.0015f) {
            ClientState.subliminal = 2;
            ClientState.subliminalKind = RAND.nextInt(SCARES.length);
        }
        if (ClientState.subliminal > 0) {
            ClientState.subliminal--;
            int size = (int) (Math.min(w, h) * 0.55f);
            int sx = w / 2 + (RAND.nextBoolean() ? w / 5 : -w / 5) - size / 2;
            image(g, SCARES[ClientState.subliminalKind], sx, (h - size) / 2, size, size, 0.6f, 0.6f, 0.6f, 0.16f);
        }
        if (ClientState.stingerTicks >= 0) {
            float a = 1.0f - (ClientState.stingerTicks + partial) / 18.0f;
            image(g, VIGNETTE, -w / 4, -h / 4, w + w / 2, h + h / 2, 0.0f, 0.0f, 0.0f, Math.max(0.0f, a));
        }
    }

    // ================================================================== the HUD and the overlays above everything
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || mc.player == null) return;
        Font font = mc.font;
        boolean calm = SounanConfig.flashReduction();
        float t = ClientState.clientTicks + partial;
        boolean hide = mc.options.hideGui || ClientState.cinematic();

        if (!hide) {
            if (holding(mc, GameItem.Kind.PHONE_CAMERA)) viewfinder(g, font, w, h, t);
            heartRate(g, font, w, h, t);
            battery(mc, g, font, w, h);
            notification(g, font, w, h, partial);
        }

        // area title
        if (ClientState.areaTicks >= 0 && ClientState.areaTitle > 0 && !hide) {
            float k = ClientState.areaTicks + partial;
            float a = k < 30 ? k / 30.0f : k > 100 ? Math.max(0.0f, (140 - k) / 40.0f) : 1.0f;
            String key = TerrainData.areaKey(ClientState.areaTitle);
            String name = I18n.get("area.sounan." + key);
            String sub = I18n.get("area.sounan." + key + ".sub");
            int cy = h / 3;
            int lineW = (int) (Math.min(1.0f, k / 40.0f) * 90);
            g.fill(w / 2 - lineW, cy + 26, w / 2 + lineW, cy + 27, argb(a * 0.6f, 0xD8D0C0));
            centered(g, font, name, w / 2, cy, argb(a, 0xE8E0D0), 2.4f);
            centered(g, font, sub, w / 2, cy + 32, argb(a * 0.7f, 0xA8A098), 0.9f);
        }
        // the inner voice
        if (ClientState.thoughtTicks >= 0 && !ClientState.thought.isEmpty()) {
            float k = ClientState.thoughtTicks + partial;
            float a = k < 12 ? k / 12.0f : k > 80 ? Math.max(0.0f, (110 - k) / 30.0f) : 1.0f;
            String text = I18n.get(ClientState.thought, ClientState.introName);
            centered(g, font, text, w / 2, h - 78, argb(a * 0.92f, 0xCFC8BC), 1.0f);
        }
        if (ClientState.subtitleTicks >= 0 && !ClientState.subtitle.isEmpty()) {
            float k = ClientState.subtitleTicks + partial;
            float a = k < 8 ? k / 8.0f : k > 95 ? Math.max(0.0f, (120 - k) / 25.0f) : 1.0f;
            String[] lines = I18n.get(ClientState.subtitle).split("\\\\n|\n");
            int y = h / 2 + 30;
            int maxW = 0;
            for (String l : lines) maxW = Math.max(maxW, font.width(l));
            g.fill(w / 2 - maxW / 2 - 8, y - 6, w / 2 + maxW / 2 + 8, y + lines.length * 11 + 4, argb(a * 0.55f, 0x0A0806));
            for (String l : lines) {
                centered(g, font, l, w / 2, y, argb(a, 0xE0D8C8), 1.0f);
                y += 11;
            }
        }
        // the public phone: the voice on the line, line after line
        if (ClientState.callKind == 900 && ClientState.callTicks >= 60) {
            int line = Math.min(8, (ClientState.callTicks - 60) / 55 + 1);
            String key = "call.sounan.900." + line;
            if (I18n.exists(key)) {
                String text = I18n.get(key, ClientState.introName);
                int tw = font.width(text);
                g.fill(w / 2 - tw / 2 - 6, h - 104, w / 2 + tw / 2 + 6, h - 90, argb(0.55f, 0x08080A));
                centered(g, font, text, w / 2, h - 101, 0xFFD8D4CC, 1.0f);
            }
        }
        if (ClientState.checkpointTicks >= 0) {
            float k = ClientState.checkpointTicks + partial;
            float a = k < 10 ? k / 10.0f : Math.max(0.0f, (100 - k) / 90.0f);
            g.fill(0, 0, w, h, argb(a * 0.06f, 0xFFF4E0));
            centered(g, font, I18n.get("hud.sounan.checkpoint"), w / 2, h / 2 + 50, argb(a * 0.8f, 0xD8D0C0), 0.9f);
        }
        if (ClientState.fogLiftTicks >= 0) {
            float k = ClientState.fogLiftTicks + partial;
            g.fill(0, 0, w, h, argb(Math.max(0.0f, 1.0f - k / 140.0f) * 0.5f, 0xE8ECEF));
        }
        // looped back by the fog
        if (ClientState.loopTicks >= 0) {
            float k = ClientState.loopTicks + partial;
            g.fill(0, 0, w, h, argb(Math.max(0.0f, 1.0f - k / 60.0f), 0x9AA2A8));
        }
        // camera flash
        if (ClientState.flashTicks >= 0 && !calm) {
            float k = ClientState.flashTicks + partial;
            g.fill(0, 0, w, h, argb(Math.max(0.0f, 1.0f - k / 12.0f) * 0.9f, 0xFFFFFF));
        }
        if (ClientState.omamoriTicks >= 0) {
            float k = ClientState.omamoriTicks + partial;
            float a = Math.max(0.0f, 1.0f - k / 50.0f);
            g.fill(0, 0, w, h, argb(a * (calm ? 0.3f : 0.75f), 0xFFE8B0));
            centered(g, font, I18n.get("hud.sounan.omamori"), w / 2, h / 2 + 40, argb(a, 0x2A1A08), 1.4f);
        }
        // being taken
        if (ClientState.jumpscareTicks >= 0) {
            int k = ClientState.jumpscareTicks;
            int kind = Mth.clamp(ClientState.jumpscareKind, 0, SCARES.length - 1);
            g.fill(0, 0, w, h, 0xFF000000);
            if (k < 34 && SounanConfig.jumpscares()) {
                float zoom = 1.0f + (k + partial) * (calm ? 0.004f : 0.03f);
                int size = (int) (Math.max(w, h) * zoom);
                int sx = calm ? 0 : RAND.nextInt(17) - 8, sy = calm ? 0 : RAND.nextInt(17) - 8;
                float flick = calm || k > 4 || k % 2 == 0 ? 1.0f : 0.6f;
                image(g, SCARES[kind], (w - size) / 2 + sx, (h - size) / 2 + sy, size, size, flick, flick, flick, 1.0f);
                if (!calm && k < 3) g.fill(0, 0, w, h, 0x50FFFFFF);
            }
        }
        if (ClientState.wakeTicks >= 0) {
            float k = ClientState.wakeTicks + partial;
            float a = k < 50 ? 1.0f : Math.max(0.0f, 1.0f - (k - 50) / 70.0f);
            g.fill(0, 0, w, h, argb(a, 0x000000));
            if (k > 10 && k < 90) {
                int[] d = ClientState.dateAfter(ClientState.days);
                String date = I18n.get("hud.sounan.date", d[1], d[2]);
                centered(g, font, date, w / 2, h / 2 - 8, argb(Math.min(1.0f, (k - 10) / 20.0f) * Math.min(1.0f, (90 - k) / 20.0f), 0xB8B0A8), 1.6f);
            }
        }
        if (ClientState.introTicks >= 0 && ClientState.introTicks < 20 * 16) intro(g, font, w, h, ClientState.introTicks + partial);
    }

    private static void intro(GuiGraphics g, Font font, int w, int h, float k) {
        float fadeOut = k > 20 * 13 ? Math.max(0.0f, 1.0f - (k - 260) / 60.0f) : 1.0f;
        g.fill(0, 0, w, h, argb(fadeOut, 0x000000));
        String[] keys = {"intro.sounan.1", "intro.sounan.2", "intro.sounan.3", "intro.sounan.4"};
        float[] at = {10, 60, 120, 190};
        for (int i = 0; i < keys.length; i++) {
            float local = k - at[i];
            if (local < 0 || local > 70) continue;
            float a = local < 15 ? local / 15.0f : local > 50 ? (70 - local) / 20.0f : 1.0f;
            centered(g, font, I18n.get(keys[i], ClientState.introName), w / 2, h / 2 - 6, argb(a * fadeOut, 0xC8C0B4), 1.3f);
        }
    }

    private static void heartRate(GuiGraphics g, Font font, int w, int h, float t) {
        float bpm = ClientState.bpm();
        float beat = (float) Math.pow(Math.max(0.0, Math.sin(t / 20.0f * bpm / 60.0f * Math.PI)), 16.0);
        float hot = Mth.clamp((bpm - 90.0f) / 80.0f, 0.0f, 1.0f);
        int r = (int) Mth.lerp(hot, 0xB8, 0xFF), gg = (int) Mth.lerp(hot, 0xB0, 0x30), b = (int) Mth.lerp(hot, 0xA8, 0x28);
        int color = 0xE0000000 | (r << 16) | (gg << 8) | b;
        int x = 10, y = h - 22;
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0);
        float s = 1.0f + beat * 0.35f;
        g.pose().scale(s, s, 1.0f);
        g.drawString(font, "♥", -3, -4, color, false);
        g.pose().popPose();
        g.drawString(font, String.valueOf(Math.round(bpm)), x + 12, y, color, false);
        g.drawString(font, "bpm", x + 12 + font.width(String.valueOf(Math.round(bpm))) + 2, y + 1, 0x80A8A098, false);
    }

    private static void battery(Minecraft mc, GuiGraphics g, Font font, int w, int h) {
        boolean flashlight = holding(mc, GameItem.Kind.FLASHLIGHT) || holding(mc, GameItem.Kind.FLASHLIGHT_ON) || holding(mc, GameItem.Kind.FLASHLIGHT_LOW);
        if (!flashlight) return;
        int x = w / 2 + 96, y = h - 18;
        float pct = ClientState.flash / 100.0f;
        int col = pct < 0.15f ? 0xFFC04030 : pct < 0.35f ? 0xFFC8A040 : 0xFFB8B4A8;
        g.fill(x, y, x + 20, y + 8, 0x90000000);
        g.fill(x + 20, y + 2, x + 22, y + 6, 0x90B8B4A8);
        int segs = (int) Math.ceil(pct * 5);
        for (int i = 0; i < segs; i++) g.fill(x + 1 + i * 4, y + 1, x + 4 + i * 4, y + 7, col);
    }

    private static void notification(GuiGraphics g, Font font, int w, int h, float partial) {
        if (ClientState.buzzTicks < 0 || ClientState.buzzText.isEmpty()) return;
        float k = ClientState.buzzTicks + partial;
        float slide = k < 8 ? k / 8.0f : k > 85 ? Math.max(0.0f, (100 - k) / 15.0f) : 1.0f;
        int bw = Math.min(w - 40, 220), bh = 26;
        int x = (w - bw) / 2, y = (int) (-bh + slide * (bh + 8));
        g.fill(x, y, x + bw, y + bh, 0xE0202226);
        g.fill(x, y, x + 3, y + bh, 0xFF4A90D0);
        String text = I18n.get(ClientState.buzzText, ClientState.introName);
        g.drawString(font, I18n.get("phone.sounan.notification"), x + 8, y + 4, 0xFF9AA0A8, false);
        g.drawString(font, font.plainSubstrByWidth(text, bw - 14), x + 8, y + 14, 0xFFE8E8EC, false);
    }

    private static void viewfinder(GuiGraphics g, Font font, int w, int h, float t) {
        int m = 18, len = 16, col = 0xD8E8F0E0;
        g.fill(m, m, m + len, m + 1, col);
        g.fill(m, m, m + 1, m + len, col);
        g.fill(w - m - len, m, w - m, m + 1, col);
        g.fill(w - m - 1, m, w - m, m + len, col);
        g.fill(m, h - m - 1, m + len, h - m, col);
        g.fill(m, h - m - len, m + 1, h - m, col);
        g.fill(w - m - len, h - m - 1, w - m, h - m, col);
        g.fill(w - m - 1, h - m - len, w - m, h - m, col);
        if (((int) (t / 10)) % 2 == 0) g.fill(m + 8, m + 10, m + 14, m + 16, 0xFFE02020);
        g.drawString(font, "REC", m + 18, m + 9, 0xFFE8E8E8, false);
        g.drawString(font, "NIGHT", m + 18, m + 20, 0xC0A8D8A0, false);
        int[] d = ClientState.date();
        String stamp = String.format("%04d/%02d/%02d  %s", d[0], d[1], d[2], ClientState.clock());
        g.drawString(font, stamp, w - m - 6 - font.width(stamp), h - m - 14, 0xFFE8E8E8, false);
        String bat = Math.round(ClientState.phone) + "%";
        g.drawString(font, bat, w - m - 6 - font.width(bat), m + 9, ClientState.phone < 15 ? 0xFFE04030 : 0xFFE8E8E8, false);
        g.fill(w / 2 - 6, h / 2, w / 2 + 7, h / 2 + 1, 0x90E8E8E8);
        g.fill(w / 2, h / 2 - 6, w / 2 + 1, h / 2 + 7, 0x90E8E8E8);
        g.drawString(font, Component.translatable("hud.sounan.camera_help"), m + 4, h - m - 14, 0x90C8C8C8, false);
    }
}
