package com.reisatsu.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reisatsu.Reisatsu;
import com.reisatsu.ReisatsuConfig;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameData;
import com.reisatsu.game.GameManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * The screen (docs/DESIGN.md §3.13, §9): atmosphere without a shader pack (vignette, grain, the red pulse of danger,
 * colour fringes at high fear — reduced while a pack draws its own), and the HUD: objective, film and お札, the
 * awareness eye, the breath gauge, room names, the finder, the instant photo, the locker slits, and every cinematic
 * layer (intro, jump scare, waking, closing time, the scream, the exorcism's dawn).
 */
public final class HorrorOverlay {
    private static final ResourceLocation VIGNETTE = Reisatsu.id("textures/gui/vignette.png");
    private static final ResourceLocation GRAIN = Reisatsu.id("textures/gui/grain.png");
    private static final ResourceLocation ICONS = Reisatsu.id("textures/gui/icons.png");
    private static final ResourceLocation SLITS = Reisatsu.id("textures/gui/locker_slits.png");
    private static final ResourceLocation LOGO = Reisatsu.id("textures/gui/title_logo.png");
    public static final int ICON_FILM = 0, ICON_OFUDA = 1, ICON_EYE_CLOSED = 2, ICON_EYE_HALF = 3, ICON_EYE_OPEN = 4, ICON_EYE_ALERT = 5,
            ICON_BREATH = 6;
    private static final RandomSource RAND = RandomSource.create();

    private HorrorOverlay() {}

    static boolean active(Minecraft mc) {
        return mc.level != null && mc.player != null && ClientState.inGame();
    }

    public static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
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

    /** A dark vignette (the texture, or a drawn stand-in). */
    static void vignette(GuiGraphics g, int w, int h, float r, float gr, float b, float a) {
        if (a <= 0.003f) return;
        if (Tex.has(VIGNETTE)) {
            image(g, VIGNETTE, 0, 0, w, h, r, gr, b, a);
            return;
        }
        int rgb = ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float k = 1.0f - i / (float) steps;
            int bw = (int) (w * 0.16f * (1.0f - k * 0.9f)), bh = (int) (h * 0.16f * (1.0f - k * 0.9f));
            int col = argb(a * 0.13f * k, rgb);
            g.fill(0, 0, w, bh, col);
            g.fill(0, h - bh, w, h, col);
            g.fill(0, bh, bw, h - bh, col);
            g.fill(w - bw, bh, w, h - bh, col);
        }
    }

    static void grain(GuiGraphics g, int w, int h, float a) {
        if (a <= 0.01f) return;
        if (Tex.has(GRAIN)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0f, 1.0f, 1.0f, Mth.clamp(a, 0.0f, 0.65f));
            g.blit(GRAIN, 0, 0, w, h, RAND.nextInt(128), RAND.nextInt(128), 128, 128, 256, 256);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            return;
        }
        int n = (int) (w * h / 900 * a);
        for (int i = 0; i < n; i++) {
            int x = RAND.nextInt(w), y = RAND.nextInt(h);
            g.fill(x, y, x + 1, y + 1, RAND.nextBoolean() ? argb(a * 0.5f, 0xFFFFFF) : argb(a * 0.6f, 0x000000));
        }
    }

    static void icon(GuiGraphics g, int index, int x, int y, int size, float a) {
        if (Tex.has(ICONS)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0f, 1.0f, 1.0f, a);
            g.blit(ICONS, x, y, size, size, index * 16, 0, 16, 16, 128, 32);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            return;
        }
        int c = argb(a, 0xE0D8C8);
        switch (index) {
            case ICON_FILM -> {
                g.fill(x + 2, y + 3, x + size - 2, y + size - 3, argb(a, 0x2A2A2A));
                g.fill(x + 4, y + 5, x + size - 4, y + size - 7, argb(a, 0x8899A0));
                g.fill(x + 2, y + size - 6, x + size - 2, y + size - 3, c);
            }
            case ICON_OFUDA -> {
                g.fill(x + 4, y + 1, x + size - 4, y + size - 1, argb(a, 0xE8DCC0));
                g.fill(x + size / 2 - 1, y + 3, x + size / 2 + 1, y + size - 3, argb(a, 0xA01818));
            }
            case ICON_BREATH -> g.fill(x + 3, y + size / 2 - 1, x + size - 3, y + size / 2 + 1, c);
            default -> {
                int open = index - ICON_EYE_CLOSED;
                int col = index == ICON_EYE_ALERT ? argb(a, 0xE02818) : c;
                g.fill(x + 1, y + size / 2, x + size - 1, y + size / 2 + 1, col);
                if (open >= 1) {
                    int hh = open == 1 ? 2 : 4;
                    g.fill(x + 3, y + size / 2 - hh, x + size - 3, y + size / 2 - hh + 1, col);
                    g.fill(x + size / 2 - 2, y + size / 2 - hh + 1, x + size / 2 + 2, y + size / 2 + 1, col);
                }
            }
        }
    }

    static void centered(GuiGraphics g, Font font, String text, int cx, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }

    // ================================================================================================ below the HUD
    public static void renderWorld(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || mc.options.hideGui) return;
        float strength = (float) ReisatsuConfig.screenEffects();
        boolean calm = ReisatsuConfig.flashReduction();
        boolean shaders = ShaderCompat.active();
        float mul = shaders ? 0.25f : 1.0f;
        float fear = Mth.lerp(partial, ClientState.fearPrev, ClientState.fearShown);
        float danger = Mth.lerp(partial, ClientState.dangerPrev, ClientState.dangerShown);
        float t = ClientState.clientTicks + partial;
        float bps = ClientState.bpm() / 60.0f;
        float beat = (float) Math.pow(Math.max(0.0, Math.sin(t / 20.0f * bps * Math.PI)), 12.0);
        if (ClientState.hiding()) {
            lockerSlits(g, w, h, t);
            return;
        }
        // heartbeat-pulsed vignette, tunnel vision
        float v = (0.30f + 0.55f * fear + 0.2f * danger + (fear > 0.45f && !calm ? beat * 0.18f * fear : 0.0f)) * strength * mul;
        vignette(g, w, h, 0.0f, 0.0f, 0.0f, v);
        if (fear > 0.6f && !shaders) vignette(g, w, h, 0.0f, 0.0f, 0.0f, (fear - 0.6f) * 1.4f * strength);
        // chromatic fringes at high fear (a pack does it properly)
        if (fear > 0.55f && !shaders && !calm) {
            float a = (fear - 0.55f) * 0.5f * strength;
            int off = (int) (2 + fear * 4);
            g.fill(0, 0, off, h, argb(a * 0.5f, 0xFF2010));
            g.fill(w - off, 0, w, h, argb(a * 0.5f, 0x1040FF));
        }
        grain(g, w, h, (0.05f + 0.16f * fear + 0.1f * danger) * strength * (shaders ? 0.0f : 1.0f));
        if (danger > 0.3f) {
            float a = (danger - 0.3f) * 0.5f * (calm ? 0.6f : 0.6f + beat * 0.4f) * strength * (shaders ? 0.4f : 1.0f);
            vignette(g, w, h, 0.5f, 0.0f, 0.02f, a);
        }
        // glitch bars when terror peaks or a spirit notices you
        if (!calm && ((!shaders && fear > 0.88f && RAND.nextFloat() < (fear - 0.88f) * 0.8f) || ClientState.glitchTicks >= 0)) {
            int bars = 1 + RAND.nextInt(ClientState.glitchTicks >= 0 ? 6 : 3);
            for (int i = 0; i < bars; i++) {
                int y = RAND.nextInt(h);
                int bh = 1 + RAND.nextInt(6);
                g.fill(0, y, w, y + bh, RAND.nextBoolean() ? 0x90000000 : 0x30FFFFFF);
            }
        }
        // at the edge of panic the dark fills with faces: two frames, then nothing
        if (!calm && ReisatsuConfig.jumpscares() && fear > 0.9f && ClientState.subliminal <= 0 && RAND.nextFloat() < 0.0012f) {
            ClientState.subliminal = 2;
            ClientState.subliminalKind = RAND.nextInt(SpiritKind.ALL.length);
        }
        if (ClientState.subliminal > 0) {
            ClientState.subliminal--;
            ResourceLocation face = Reisatsu.id("textures/gui/jumpscare_" + SpiritKind.ALL[ClientState.subliminalKind].id + ".png");
            if (Tex.has(face)) {
                int size = (int) (Math.min(w, h) * 0.55f);
                int sx = w / 2 + (RAND.nextBoolean() ? w / 5 : -w / 5) - size / 2;
                image(g, face, sx, (h - size) / 2, size, size, 0.6f, 0.6f, 0.6f, 0.14f);
            }
        }
        if (ClientState.stingerTicks >= 0) {
            float a = 1.0f - (ClientState.stingerTicks + partial) / 18.0f;
            vignette(g, w, h, 0.0f, 0.0f, 0.0f, Math.max(0.0f, a));
        }
    }

    private static void lockerSlits(GuiGraphics g, int w, int h, float t) {
        if (Tex.has(SLITS)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(SLITS, 0, 0, w, h, 0, 0, 256, 144, 256, 144);
            RenderSystem.disableBlend();
            return;
        }
        // drawn slits: dark steel with three horizontal louvres to look out through
        int band = h / 9;
        int top = h / 2 - band * 2;
        int col = 0xF4101214;
        g.fill(0, 0, w, top, col);
        for (int i = 0; i < 3; i++) {
            int y0 = top + i * band + band / 3;
            g.fill(0, y0 + band / 3, w, y0 + band, col);
            g.fill(0, y0 + band / 3, w, y0 + band / 3 + 1, 0xF0303438);
        }
        g.fill(0, top + 3 * band + band / 3, w, h, col);
        g.fill(0, 0, w / 7, h, col);
        g.fill(w - w / 7, 0, w, h, col);
    }

    // ================================================================================================ the HUD
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) return;
        Font font = mc.font;
        float t = ClientState.clientTicks + partial;
        boolean hide = mc.options.hideGui;
        boolean cinematic = ClientState.locked() || ClientState.exorcismStage >= 0;
        if (!hide && Finder.active(mc)) Finder.render(g, font, w, h, partial);
        if (!hide && !cinematic) {
            objective(g, font, w, h, partial);
            counters(g, font, w, h);
            if (!Finder.active(mc)) eye(g, w, h, t);
            breath(g, font, w, h);
            hint(g, font, w, h, partial);
        }
        if (!hide && ClientState.areaTicks >= 0 && !ClientState.areaKey.isEmpty()) {
            float k = ClientState.areaTicks + partial;
            float a = k < 15 ? k / 15.0f : k > 80 ? Math.max(0.0f, (110 - k) / 30.0f) : 1.0f;
            String name = I18n.get("area.reisatsu." + ClientState.areaKey);
            centered(g, font, name, w / 2, 26, argb(a * 0.85f, 0xE0D8C8), 1.3f);
            g.fill(w / 2 - 30, 40, w / 2 + 30, 41, argb(a * 0.4f, 0xE0D8C8));
        }
        if (ClientState.thoughtTicks >= 0 && !ClientState.thought.isEmpty()) {
            float k = ClientState.thoughtTicks + partial;
            float a = k < 12 ? k / 12.0f : k > 80 ? Math.max(0.0f, (110 - k) / 30.0f) : 1.0f;
            centered(g, font, I18n.get(ClientState.thought), w / 2, h / 2 + 46, argb(a * 0.9f, 0xCFC8BC), 1.0f);
        }
        PhotoView.render(g, font, w, h, partial);
        if (!hide) Dialogue.render(g, font, w, h, partial);
        overlays(mc, g, font, w, h, partial, t);
    }

    private static String objectiveText() {
        int phase = ClientState.phase;
        return switch (phase) {
            case GameData.PHASE_INVESTIGATE -> I18n.get("objective.reisatsu.investigate", ClientState.capturedFive());
            case GameData.PHASE_CLOSING -> ClientState.isCaptured(SpiritKind.MANAGER) ? I18n.get("objective.reisatsu.return")
                    : I18n.get("objective.reisatsu.manager");
            case GameData.PHASE_RETURN -> I18n.get("objective.reisatsu.return");
            case GameData.PHASE_EXORCISM -> I18n.get("objective.reisatsu.exorcism");
            default -> "";
        };
    }

    private static void objective(GuiGraphics g, Font font, int w, int h, float partial) {
        String text = objectiveText();
        if (text.isEmpty()) return;
        float flash = ClientState.objectiveTicks >= 0 ? Math.max(0.0f, 1.0f - (ClientState.objectiveTicks + partial) / 60.0f) : 0.0f;
        int x = 10, y = 10;
        g.fill(x - 4, y - 3, x + font.width(text) + 6, y + 11, argb(0.35f + flash * 0.3f, 0x050505));
        g.fill(x - 4, y - 3, x - 2, y + 11, argb(0.9f, flash > 0 ? 0xE8C060 : 0xB04030));
        g.drawString(font, text, x + 1, y, argb(0.92f, flash > 0 ? 0xFFF0C8 : 0xE8E0D0), false);
        // photos the medium has not seen yet
        int unsubmitted = ClientState.captured & ~ClientState.submitted;
        if (unsubmitted != 0 && ClientState.phase != GameData.PHASE_EXORCISM) {
            String sub = I18n.get("objective.reisatsu.hand_in", Integer.bitCount(unsubmitted));
            g.drawString(font, sub, x + 1, y + 13, argb(0.6f, 0xC8C0B0), false);
        }
    }

    private static void counters(GuiGraphics g, Font font, int w, int h) {
        int x = w - 58, y = h - 22;
        icon(g, ICON_FILM, x, y - 2, 12, 0.9f);
        g.drawString(font, "×" + ClientState.film, x + 14, y, ClientState.film <= 0 ? 0xFFE04030 : 0xE8E0D0D0, false);
        icon(g, ICON_OFUDA, x + 30, y - 2, 12, 0.9f);
        g.drawString(font, "×" + ClientState.ofuda, x + 44, y, ClientState.ofuda <= 0 ? 0xFFE04030 : 0xE8E0D0D0, false);
    }

    /** The awareness eye: closed, half open, open; red when it is about to notice you. */
    private static void eye(GuiGraphics g, int w, int h, float t) {
        float a = ClientState.awareShown;
        int idx = a >= 0.8f ? ICON_EYE_ALERT : a >= 0.5f ? ICON_EYE_OPEN : a >= 0.2f ? ICON_EYE_HALF : ICON_EYE_CLOSED;
        float alpha = a < 0.05f ? 0.25f : 0.55f + 0.45f * Math.min(1.0f, a);
        if (idx == ICON_EYE_ALERT && (int) (t / 3) % 2 == 0) alpha = 1.0f;
        icon(g, idx, w / 2 - 6, h / 2 + 10, 12, alpha);
    }

    private static void breath(GuiGraphics g, Font font, int w, int h) {
        boolean holding = ClientState.flag(GameManager.F_BREATH);
        if (!holding && ClientState.breath >= 0.999f) return;
        int bw = 60, x = w / 2 - bw / 2, y = h / 2 + 26;
        float b = Mth.clamp(ClientState.breath, 0.0f, 1.0f);
        int col = b < 0.25f ? 0xD0E04030 : holding ? 0xD0D8E0E8 : 0x80A0A8B0;
        g.fill(x, y, x + bw, y + 2, 0x60000000);
        g.fill(x, y, x + (int) (bw * b), y + 2, col);
        if (holding) icon(g, ICON_BREATH, x - 14, y - 5, 12, 0.85f);
    }

    private static void hint(GuiGraphics g, Font font, int w, int h, float partial) {
        if (ClientState.hintTicks < 0) return;
        float k = ClientState.hintTicks + partial;
        float a = k < 20 ? k / 20.0f : k > 240 ? Math.max(0.0f, (280 - k) / 40.0f) : 1.0f;
        String[] lines = I18n.get("hint.reisatsu.controls").split("\n");
        int y = h / 2 - lines.length * 6;
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, font.width(l));
        g.fill(8, y - 6, 18 + maxW, y + lines.length * 11 + 4, argb(a * 0.5f, 0x050505));
        for (String l : lines) {
            g.drawString(font, l, 13, y, argb(a * 0.9f, 0xD8D0C0), false);
            y += 11;
        }
    }

    // ================================================================================================ cinematic layers
    private static void overlays(Minecraft mc, GuiGraphics g, Font font, int w, int h, float partial, float t) {
        boolean calm = ReisatsuConfig.flashReduction();
        // closing time: 「閉店」 flashes red
        if (ClientState.closingTicks >= 0) {
            float k = ClientState.closingTicks + partial;
            float a = k < 10 ? k / 10.0f : Math.max(0.0f, 1.0f - (k - 60) / 60.0f);
            if (k < 60 && !calm && (int) (k / 4) % 2 == 0) a *= 0.6f;
            centered(g, font, I18n.get("hud.reisatsu.closed"), w / 2, h / 3, argb(a, 0xE02818), 3.2f);
            g.fill(0, 0, w, h, argb(Math.max(0.0f, 0.18f - k / 400.0f), 0xFFF4E0));
        }
        if (ClientState.screamTicks >= 0) {
            float k = ClientState.screamTicks + partial;
            int left = Math.max(0, 3 - (int) (k / 20));
            if (k < 60) centered(g, font, I18n.get("hud.reisatsu.run", left), w / 2, h / 2 + 30, argb(0.9f, 0xE8E0E0), 1.4f);
            if (!calm) g.fill(0, 0, w, h, argb(0.12f + 0.1f * Mth.sin(k * 1.3f), 0x400000));
        }
        if (ClientState.submitTicks >= 0 && ClientState.submitKind >= 0) {
            float k = ClientState.submitTicks + partial;
            float a = k < 15 ? k / 15.0f : Math.max(0.0f, (90 - k) / 30.0f);
            SpiritKind sk = SpiritKind.byIndex(ClientState.submitKind);
            if (sk != null) centered(g, font, I18n.get(sk.nameKey()), w / 2, h / 3, argb(a * 0.9f, 0xB01818), 2.2f);
        }
        if (ClientState.releaseTicks >= 0) {
            float k = ClientState.releaseTicks + partial;
            g.fill(0, 0, w, h, argb(Math.max(0.0f, 0.22f - k / 300.0f), 0xFFF6E8));
        }
        // a fade through black (teleports in cinematic moments)
        if (ClientState.fadeTicks >= 0) {
            float k = ClientState.fadeTicks + partial;
            int L = ClientState.fadeLength;
            float a = k < L * 0.5f ? k / (L * 0.5f) : k < L ? 1.0f : Math.max(0.0f, 1.0f - (k - L) / L);
            g.fill(0, 0, w, h, argb(a, 0x000000));
        }
        // dawn: the light comes up warm, then white
        if (ClientState.dawnTicks >= 0) {
            float k = ClientState.dawnTicks + partial;
            float a = Mth.clamp(k / 140.0f, 0.0f, 1.0f);
            g.fillGradient(0, 0, w, h, argb(a * 0.55f, 0xFFE2B0), argb(a * 0.8f, 0xFFF4E2));
        }
        // being taken
        if (ClientState.jumpscareTicks >= 0) {
            int k = ClientState.jumpscareTicks;
            g.fill(0, 0, w, h, 0xFF000000);
            SpiritKind sk = SpiritKind.byIndex(ClientState.jumpscareKind);
            ResourceLocation face = sk == null ? null : Reisatsu.id("textures/gui/jumpscare_" + sk.id + ".png");
            if (face != null && ReisatsuConfig.jumpscares() && k < 13) {
                float zoom = 1.0f + (k + partial) * (calm ? 0.004f : 0.035f);
                int size = (int) (Math.max(w, h) * zoom);
                int sx = calm ? 0 : RAND.nextInt(17) - 8, sy = calm ? 0 : RAND.nextInt(17) - 8;
                float flick = calm || k > 3 || k % 2 == 0 ? 1.0f : 0.55f;
                if (Tex.has(face)) image(g, face, (w - size) / 2 + sx, (h - size) / 2 + sy, size, size, flick, flick, flick, 1.0f);
                else centered(g, font, I18n.get(sk.roleKey()), w / 2 + sx, h / 2 + sy, 0xFFE8E8E8, 4.0f);
                if (!calm && k < 2) g.fill(0, 0, w, h, 0x50FFFFFF);
            }
        }
        if (ClientState.caughtTicks >= 0) g.fill(0, 0, w, h, 0xFF000000);
        if (ClientState.wakeTicks >= 0) {
            float k = ClientState.wakeTicks + partial;
            float a = k < 40 ? 1.0f : Math.max(0.0f, 1.0f - (k - 40) / 70.0f);
            g.fill(0, 0, w, h, argb(a, 0x000000));
            if (k > 8 && k < 80) {
                float ta = Math.min(1.0f, (k - 8) / 15.0f) * Math.min(1.0f, (80 - k) / 15.0f);
                centered(g, font, I18n.get("hud.reisatsu.ofuda_burnt", ClientState.wakeOfuda), w / 2, h / 2 - 6, argb(ta, 0xD8C8A0), 1.3f);
            }
        }
        if (ClientState.introTicks >= 0) intro(g, font, w, h, ClientState.introTicks + partial);
    }

    private static void intro(GuiGraphics g, Font font, int w, int h, float k) {
        int textEnd = com.reisatsu.game.Story.INTRO_TEXT;
        int fade = com.reisatsu.game.Story.INTRO_FADE;
        float black = k < textEnd ? 1.0f : Math.max(0.0f, 1.0f - (k - textEnd) / fade);
        if (black > 0.0f) g.fill(0, 0, w, h, argb(black, 0x000000));
        for (int i = 0; i < 5; i++) {
            float local = k - (8 + i * 42);
            if (local < 0 || local > 44) continue;
            float a = local < 10 ? local / 10.0f : local > 34 ? (44 - local) / 10.0f : 1.0f;
            String text = I18n.get("intro.reisatsu." + (i + 1));
            String[] lines = text.split("\n");
            int y = h / 2 - lines.length * 7;
            for (String l : lines) {
                centered(g, font, l, w / 2, y, argb(a, 0xC8C0B4), 1.25f);
                y += 14;
            }
        }
        // the title, as the parking lot fades in
        float lt = k - textEnd;
        if (lt >= 0 && lt < 90) {
            float a = lt < 15 ? lt / 15.0f : lt > 60 ? (90 - lt) / 30.0f : 1.0f;
            int lw = Math.min(w * 3 / 5, 384), lh = lw / 2;
            ResourceLocation logo = LOGO;
            if (Tex.has(logo)) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                g.setColor(1.0f, 1.0f, 1.0f, a);
                g.blit(logo, (w - lw) / 2, (h - lh) / 2 - 20, lw, lh, 0, 0, 512, 256, 512, 256);
                g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.disableBlend();
            } else {
                centered(g, font, "霊撮", w / 2, h / 2 - 40, argb(a, 0xE8E0D0), 5.0f);
                centered(g, font, "― REISATSU ―", w / 2, h / 2 + 10, argb(a * 0.8f, 0xB01818), 1.5f);
            }
        }
        if (ClientState.introSkippable) {
            String s = I18n.get("hud.reisatsu.skip");
            g.drawString(font, s, w - font.width(s) - 10, h - 16, argb(0.55f, 0xB8B0A8), false);
        }
    }
}
