package com.hitori.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.hitori.Hitori;
import com.hitori.HitoriConfig;
import com.hitori.game.Clock;
import com.hitori.game.GameManager;
import com.hitori.game.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * The screen (docs/DESIGN.md §3.8, §9): atmosphere without a shader pack (vignette, grain, the red pulse of danger,
 * colour fringes at high fear — reduced while a pack draws its own), the closet's darkness and the slit of the
 * fusuma, the black of closed eyes with the TV flickering through the eyelids, and the HUD — the current step top
 * left, the clock top right, the salt water in your mouth and your breath bottom right, subtitles, the phone; the
 * camera app's CCTV overlay instead of all of it; and the cinematic layers (3 a.m., the jump scare, morning, endings).
 */
public final class HorrorOverlay {
    private static final ResourceLocation VIGNETTE = Hitori.id("textures/gui/vignette.png");
    private static final ResourceLocation GRAIN = Hitori.id("textures/gui/grain.png");
    private static final ResourceLocation ICONS = Hitori.id("textures/gui/icons.png");
    private static final ResourceLocation CLOSET = Hitori.id("textures/gui/closet_dark.png");
    private static final ResourceLocation SLIT = Hitori.id("textures/gui/fusuma_slit.png");
    private static final ResourceLocation LOGO = Hitori.id("textures/gui/title_logo.png");
    private static final ResourceLocation JS_DOLL = Hitori.id("textures/gui/jumpscare_doll.png");
    private static final ResourceLocation JS_WOMAN = Hitori.id("textures/gui/jumpscare_woman.png");
    /** icons.png: a row of 16×16 cells (128×32 sheet). */
    public static final int ICON_CUP = 0, ICON_BREATH = 1, ICON_REC = 2, ICON_CHECK = 7, ICON_BOX = 8;
    private static final RandomSource RAND = RandomSource.create();

    private HorrorOverlay() {}

    static boolean active(Minecraft mc) {
        return mc.level != null && mc.player != null && ClientState.inGame();
    }

    public static int argb(float a, int rgb) {
        return (Mth.clamp((int) (a * 255.0f), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    static void image(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int texW, int texH, float r, float gr, float b, float a) {
        if (a <= 0.003f) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(r, gr, b, Mth.clamp(a, 0.0f, 1.0f));
        g.blit(tex, x, y, w, h, 0, 0, texW, texH, texW, texH);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    static void vignette(GuiGraphics g, int w, int h, float r, float gr, float b, float a) {
        if (a <= 0.003f) return;
        if (Tex.has(VIGNETTE)) {
            image(g, VIGNETTE, 0, 0, w, h, 256, 256, r, gr, b, a);
            return;
        }
        int rgb = ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float k = 1.0f - i / (float) steps;
            int bw = (int) (w * 0.18f * (1.0f - k * 0.9f)), bh = (int) (h * 0.18f * (1.0f - k * 0.9f));
            int col = argb(a * 0.13f * k, rgb);
            g.fill(0, 0, w, bh, col);
            g.fill(0, h - bh, w, h, col);
            g.fill(0, bh, bw, h - bh, col);
            g.fill(w - bw, bh, w, h - bh, col);
        }
    }

    static void grain(GuiGraphics g, int w, int h, float a) {
        grainIn(g, 0, 0, w, h, a);
    }

    /** Film grain / video noise in a rectangle. */
    public static void grainIn(GuiGraphics g, int x0, int y0, int x1, int y1, float a) {
        if (a <= 0.01f) return;
        int w = x1 - x0, h = y1 - y0;
        if (Tex.has(GRAIN)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0f, 1.0f, 1.0f, Mth.clamp(a, 0.0f, 0.75f));
            g.blit(GRAIN, x0, y0, w, h, RAND.nextInt(128), RAND.nextInt(128), 128, 128, 256, 256);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            return;
        }
        int n = (int) (w * h / 700.0f * a);
        for (int i = 0; i < n; i++) {
            int x = x0 + RAND.nextInt(Math.max(1, w)), y = y0 + RAND.nextInt(Math.max(1, h));
            g.fill(x, y, x + 1, y + 1, RAND.nextBoolean() ? argb(a * 0.6f, 0xFFFFFF) : argb(a * 0.7f, 0x000000));
        }
    }

    static void icon(GuiGraphics g, int index, int x, int y, int size, float a) {
        if (Tex.has(ICONS)) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0f, 1.0f, 1.0f, a);
            g.blit(ICONS, x, y, size, size, (index % 8) * 16, (index / 8) * 16, 16, 16, 128, 32);
            g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.disableBlend();
            return;
        }
        int c = argb(a, 0xE0D8C8);
        switch (index) {
            case ICON_CUP -> {
                g.fill(x + 3, y + 2, x + size - 3, y + 3, c);
                g.fill(x + 3, y + 2, x + 4, y + size - 2, c);
                g.fill(x + size - 4, y + 2, x + size - 3, y + size - 2, c);
                g.fill(x + 3, y + size - 3, x + size - 3, y + size - 2, c);
                g.fill(x + 4, y + size / 2, x + size - 4, y + size - 3, argb(a * 0.8f, 0x9CC4D8));
            }
            case ICON_BREATH -> {
                g.fill(x + 3, y + size / 2 - 1, x + size - 3, y + size / 2 + 1, c);
                g.fill(x + 5, y + size / 2 - 4, x + size - 5, y + size / 2 - 3, argb(a * 0.6f, 0xE0D8C8));
            }
            default -> g.fill(x + 3, y + 3, x + size - 3, y + size - 3, c);
        }
    }

    public static void centered(GuiGraphics g, Font font, String text, int cx, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }

    // ================================================================================================ below the HUD
    public static void renderWorld(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc) || mc.options.hideGui && !AutoShots.active()) return;
        if (CameraApp.isOpen() || FrameCapture.capturing()) return;
        float strength = (float) HitoriConfig.screenEffects();
        boolean calm = HitoriConfig.flashReduction();
        boolean shaders = ShaderCompat.active();
        float mul = shaders ? 0.3f : 1.0f;
        float fear = Mth.lerp(partial, ClientState.fearPrev, ClientState.fearShown);
        float danger = Mth.lerp(partial, ClientState.dangerPrev, ClientState.dangerShown);
        float t = ClientState.clientTicks + partial;
        float bps = ClientState.bpm() / 60.0f;
        float beat = (float) Math.pow(Math.max(0.0, Math.sin(t / 20.0f * bps * Math.PI)), 12.0);
        if (ClientState.eyesClosed()) {
            eyelids(g, w, h, t, calm);
            return;
        }
        if (Peek.active()) {
            slit(g, w, h, t);
            return;
        }
        if (ClientState.hiding()) {
            closet(g, w, h, t, ClientState.inCloset());
        }
        // heartbeat-pulsed vignette, tunnel vision
        float v = (0.32f + 0.5f * fear + 0.2f * danger + (fear > 0.45f && !calm ? beat * 0.16f * fear : 0.0f)) * strength * mul;
        vignette(g, w, h, 0.0f, 0.0f, 0.0f, v);
        if (fear > 0.6f && !shaders) vignette(g, w, h, 0.0f, 0.0f, 0.0f, (fear - 0.6f) * 1.3f * strength);
        if (fear > 0.55f && !shaders && !calm) {
            float a = (fear - 0.55f) * 0.5f * strength;
            int off = (int) (2 + fear * 4);
            g.fill(0, 0, off, h, argb(a * 0.5f, 0xFF2010));
            g.fill(w - off, 0, w, h, argb(a * 0.5f, 0x1040FF));
        }
        grain(g, w, h, (0.05f + 0.15f * fear + 0.1f * danger) * strength * (shaders ? 0.0f : 1.0f));
        if (danger > 0.3f) {
            float a = (danger - 0.3f) * 0.5f * (calm ? 0.6f : 0.6f + beat * 0.4f) * strength * (shaders ? 0.4f : 1.0f);
            vignette(g, w, h, 0.45f, 0.0f, 0.02f, a);
        }
        if (!calm && !shaders && fear > 0.88f && RAND.nextFloat() < (fear - 0.88f) * 0.8f) {
            int bars = 1 + RAND.nextInt(3);
            for (int i = 0; i < bars; i++) {
                int y = RAND.nextInt(h);
                g.fill(0, y, w, y + 1 + RAND.nextInt(5), RAND.nextBoolean() ? 0x90000000 : 0x30FFFFFF);
            }
        }
        // at the edge of panic, the dark has a face: two frames, then nothing
        if (!calm && HitoriConfig.jumpscares() && fear > 0.9f && ClientState.subliminal <= 0 && RAND.nextFloat() < 0.0010f) ClientState.subliminal = 2;
        if (ClientState.subliminal > 0) {
            ClientState.subliminal--;
            if (Tex.has(JS_DOLL)) {
                int size = (int) (Math.min(w, h) * 0.5f);
                int sx = w / 2 + (RAND.nextBoolean() ? w / 5 : -w / 5) - size / 2;
                image(g, JS_DOLL, sx, (h - size) / 2, size, size, 256, 256, 0.6f, 0.6f, 0.6f, 0.12f);
            }
        }
        if (ClientState.stingerTicks >= 0) {
            float a = 1.0f - (ClientState.stingerTicks + partial) / 18.0f;
            vignette(g, w, h, 0.0f, 0.0f, 0.0f, Math.max(0.0f, a));
        }
    }

    /** Eyes closed: black, faint phosphenes, the TV's flicker coming red through the eyelids. */
    private static void eyelids(GuiGraphics g, int w, int h, float t, boolean calm) {
        g.fill(0, 0, w, h, 0xFF000000);
        float flick = 0.05f + 0.05f * (float) Math.abs(Math.sin(t * 0.9f) * Math.sin(t * 2.3f + 1.0f));
        if (!calm && RAND.nextInt(6) == 0) flick += 0.04f;
        g.fillGradient(0, 0, w, h, argb(flick * 0.7f, 0x3A0806), argb(flick, 0x5A0C08));
        for (int i = 0; i < 14; i++) {
            long k = (long) (t / 7) * 31 + i * 977L;
            int x = (int) ((k * 2654435761L >>> 8) % Math.max(1, w)), y = (int) ((k * 40503L >>> 6) % Math.max(1, h));
            g.fill(x, y, x + 2, y + 2, argb(0.08f, 0x8A6A90));
        }
    }

    /** Inside the closet: close darkness, the futon's edge above, the gap of light. */
    private static void closet(GuiGraphics g, int w, int h, float t, boolean oshiire) {
        if (Tex.has(CLOSET)) {
            image(g, CLOSET, 0, 0, w, h, 256, 256, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            vignette(g, w, h, 0.0f, 0.0f, 0.0f, 1.2f);
            g.fill(0, 0, w, h / 7, 0xC8060606);
            g.fill(0, h / 7, w, h / 7 + 2, 0x60282420);
        }
        // the gap of the fusuma: the TV's flicker in a thin vertical line, when you face it
        Minecraft mc = Minecraft.getInstance();
        if (oshiire && mc.player != null && com.hitori.world.Layout.has("closet_peek")) {
            float base = com.hitori.world.Layout.marker("closet_peek").yaw();
            float d = Mth.wrapDegrees(mc.player.getYRot() - base);
            if (Math.abs(d) < 45.0f) {
                int x = (int) (w / 2.0f - d / 45.0f * w * 0.6f);
                float flick = ClientState.flag(GameManager.F_TV) ? 0.35f + 0.25f * (float) Math.abs(Math.sin(t * 1.3f) * Math.cos(t * 0.37f)) : 0.08f;
                g.fill(x, h / 6, x + 1, h - h / 8, argb(flick, 0xBFD4FF));
                g.fill(x - 2, h / 6, x + 3, h - h / 8, argb(flick * 0.25f, 0x8FA8E0));
            }
        }
    }

    /** Peeking: the slit of the fusuma. */
    private static void slit(GuiGraphics g, int w, int h, float t) {
        if (Tex.has(SLIT)) {
            image(g, SLIT, 0, 0, w, h, 256, 256, 1.0f, 1.0f, 1.0f, 1.0f);
            return;
        }
        int sw = Math.max(10, w / 14);
        int open = (int) Math.min(sw, sw * Math.min(1.0f, Peek.activeTicks() / 6.0f));
        int x0 = w / 2 - open / 2, x1 = w / 2 + open / 2;
        g.fill(0, 0, x0, h, 0xFF050505);
        g.fill(x1, 0, w, h, 0xFF050505);
        // the paper edges catching the light
        g.fill(x0 - 2, 0, x0, h, 0xFF2A2620);
        g.fill(x1, 0, x1 + 2, h, 0xFF2A2620);
        g.fill(x0, 0, x1, h / 10, 0xE0050505);
        g.fill(x0, h - h / 10, x1, h, 0xE0050505);
    }

    // ================================================================================================ the HUD
    public static void renderHud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!active(mc)) return;
        Font font = mc.font;
        float t = ClientState.clientTicks + partial;
        boolean hide = mc.options.hideGui && !AutoShots.active();
        if (FrameCapture.capturing()) {
            g.fill(0, 0, w, h, 0xFF000000);
            overlays(mc, g, font, w, h, partial, t);
            return;
        }
        if (CameraApp.isOpen()) {
            if (!hide) CameraApp.render(g, font, w, h, partial);
            if (!hide) PhoneUi.render(g, font, w, h, partial);
            if (!hide) Dialogue.render(g, font, w, h, partial);
            overlays(mc, g, font, w, h, partial, t);
            return;
        }
        boolean cinematic = ClientState.flag(GameManager.F_ENDING) || ClientState.jumpscareTicks >= 0 || ClientState.caughtTicks >= 0
                || ClientState.eyesClosed() || ClientState.introTicks >= 0;
        if (!hide && !cinematic) {
            step(g, font, w, h, partial);
            clock(g, font, w, h);
            mouth(g, font, w, h, t);
            action(g, font, w, h);
            hint(g, font, w, h, partial);
            if (ClientState.hiding()) hidingHint(g, font, w, h);
        }
        if (!hide && ClientState.thoughtTicks >= 0 && !ClientState.thought.isEmpty()) {
            float k = ClientState.thoughtTicks + partial;
            float a = k < 12 ? k / 12.0f : k > 90 ? Math.max(0.0f, (120 - k) / 30.0f) : 1.0f;
            centered(g, font, ClientPacketHandler.format(ClientState.thought), w / 2, h / 2 + 40, argb(a * 0.9f, 0xCFC8BC), 1.0f);
        }
        if (!hide && ClientState.ruleTicks >= 0) {
            float k = ClientState.ruleTicks + partial;
            float a = k < 10 ? k / 10.0f : k > 90 ? Math.max(0.0f, (120 - k) / 30.0f) : 1.0f;
            centered(g, font, I18n.get("hud.hitori.rule_broken"), w / 2, 40, argb(a, 0xC02018), 1.2f);
            centered(g, font, I18n.get("rule.hitori.short." + ClientState.ruleKey), w / 2, 54, argb(a * 0.8f, 0xC8B0A8), 1.0f);
        }
        if (!hide) PhoneUi.render(g, font, w, h, partial);
        if (!hide) Dialogue.render(g, font, w, h, partial);
        overlays(mc, g, font, w, h, partial, t);
    }

    /** 「次：米を詰める」 / 「隠れる」 / 「人形を探す」, top left. */
    private static void step(GuiGraphics g, Font font, int w, int h, float partial) {
        int hud = ClientState.hud;
        String text;
        if (hud == Step.HUD_WAIT) text = I18n.get("hud.hitori.wait_three");
        else if (hud == Step.HUD_HIDING) text = I18n.get("hud.hitori.hiding");
        else if (hud >= 0 && hud < Step.ALL.length) text = I18n.get("hud.hitori.next", I18n.get("step.hitori." + Step.ALL[hud].id));
        else return;
        float flash = ClientState.stepFlash >= 0 ? Math.max(0.0f, 1.0f - (ClientState.stepFlash + partial) / 60.0f) : 0.0f;
        int x = 10, y = 10;
        g.fill(x - 4, y - 3, x + font.width(text) + 6, y + 11, argb(0.35f + flash * 0.3f, 0x050505));
        g.fill(x - 4, y - 3, x - 2, y + 11, argb(0.9f, flash > 0 ? 0xE8C060 : 0xA02018));
        g.drawString(font, text, x + 1, y, argb(0.92f, flash > 0 ? 0xFFF0C8 : 0xE8E0D0), false);
        if (ClientState.phase == com.hitori.game.GameData.PHASE_PREP) {
            int n = 0;
            for (Step s : Step.PREP) if (ClientState.done(s)) n++;
            String sub = I18n.get("hud.hitori.prep_count", n, Step.PREP.length);
            g.drawString(font, sub, x + 1, y + 13, argb(0.55f, 0xC8C0B0), false);
        }
    }

    /** The night's clock, top right. */
    private static void clock(GuiGraphics g, Font font, int w, int h) {
        if (ClientState.phase == com.hitori.game.GameData.PHASE_IDLE) return;
        String s = Clock.hhmm(ClientState.clock);
        int col = ClientState.clock >= Clock.THREE && ClientState.clock < Clock.THREE + 60 ? 0xE04030 : 0xE8E0D0;
        g.pose().pushPose();
        g.pose().translate(w - 10 - font.width(s) * 1.6f, 8, 0);
        g.pose().scale(1.6f, 1.6f, 1.0f);
        g.drawString(font, s, 0, 0, argb(0.85f, col), true);
        g.pose().popPose();
    }

    /** The salt water in your mouth (a cup), and your held breath, bottom right. */
    private static void mouth(GuiGraphics g, Font font, int w, int h, float t) {
        boolean holding = ClientState.breathHeld();
        float m = Mth.clamp(ClientState.mouthShown, 0.0f, 1.0f);
        boolean show = m > 0.01f || ClientState.flag(GameManager.F_HAS_WATER) && ClientState.phase >= com.hitori.game.GameData.PHASE_RITUAL;
        int x = w - 92, y = h - 30;
        if (show) {
            icon(g, ICON_CUP, x, y - 4, 14, m > 0.01f ? 0.95f : 0.4f);
            g.fill(x + 18, y + 1, x + 78, y + 4, 0x60000000);
            int col = m < 0.2f ? 0xD0E04030 : 0xD09CC4D8;
            g.fill(x + 18, y + 1, x + 18 + (int) (60 * m), y + 4, col);
            String label = I18n.get(m > 0.01f ? "hud.hitori.mouth" : "hud.hitori.mouth_empty");
            g.drawString(font, label, x + 18, y - 9, argb(0.6f, m > 0.01f ? 0xC8D8E0 : 0xC08070), false);
        }
        if (holding || ClientState.breath < 0.999f) {
            float b = Mth.clamp(ClientState.breath, 0.0f, 1.0f);
            int by = y - 22;
            icon(g, ICON_BREATH, x, by - 4, 14, holding ? 0.9f : 0.5f);
            g.fill(x + 18, by + 1, x + 78, by + 3, 0x60000000);
            int col = b < 0.25f ? ((int) (t / 4) % 2 == 0 ? 0xF0E04030 : 0xA0E04030) : holding ? 0xD0D8E0E8 : 0x80A0A8B0;
            g.fill(x + 18, by + 1, x + 18 + (int) (60 * b), by + 3, col);
        }
    }

    /** A long action (cutting, sewing...): its name and a bar in the middle. */
    private static void action(GuiGraphics g, Font font, int w, int h) {
        if (!ClientState.flag(GameManager.F_BUSY) || ClientState.action.isEmpty() || ClientState.action.equals("count")) return;
        String label = I18n.get("action.hitori." + ClientState.action);
        int bw = 90, x = w / 2 - bw / 2, y = h / 2 + 18;
        centered(g, font, label, w / 2, y - 12, 0xD8E0D8D0, 1.0f);
        g.fill(x, y, x + bw, y + 3, 0x80000000);
        g.fill(x, y, x + (int) (bw * Mth.clamp(ClientState.actionProgress, 0.0f, 1.0f)), y + 3, 0xE0E8E0D0);
    }

    private static void hint(GuiGraphics g, Font font, int w, int h, float partial) {
        if (ClientState.hintTicks < 0) return;
        float k = ClientState.hintTicks + partial;
        float a = k < 20 ? k / 20.0f : k > 280 ? Math.max(0.0f, (320 - k) / 40.0f) : 1.0f;
        String[] lines = I18n.get(ClientState.hintKind == 1 ? "hint.hitori.printout" : "hint.hitori.controls").split("\n");
        int y = h / 2 - lines.length * 6;
        int maxW = 0;
        for (String l : lines) maxW = Math.max(maxW, font.width(l));
        g.fill(8, y - 6, 18 + maxW, y + lines.length * 11 + 4, argb(a * 0.5f, 0x050505));
        for (String l : lines) {
            g.drawString(font, l, 13, y, argb(a * 0.9f, 0xD8D0C0), false);
            y += 11;
        }
    }

    private static void hidingHint(GuiGraphics g, Font font, int w, int h) {
        String s = I18n.get(ClientState.inCloset() ? "hud.hitori.closet_keys" : "hud.hitori.western_keys");
        g.drawString(font, s, (w - font.width(s)) / 2, h - 46, 0x70B0A8A0, false);
    }

    // ================================================================================================ cinematic layers
    private static void overlays(Minecraft mc, GuiGraphics g, Font font, int w, int h, float partial, float t) {
        boolean calm = HitoriConfig.flashReduction();
        // text is batched and drawn last: what was written before must be under these layers, not over them
        g.flush();
        // 3 a.m.
        if (ClientState.threeTicks >= 0) {
            float k = ClientState.threeTicks + partial;
            float a = k < 20 ? k / 20.0f : k > 110 ? Math.max(0.0f, (160 - k) / 50.0f) : 1.0f;
            g.fill(0, 0, w, h, argb(a * 0.35f, 0x000000));
            centered(g, font, I18n.get("hud.hitori.three_am"), w / 2, h / 3, argb(a, 0xE8E0D8), 2.6f);
            centered(g, font, I18n.get("hud.hitori.three_am_sub"), w / 2, h / 3 + 28, argb(a * 0.8f, 0xB01818), 1.2f);
        }
        // the woman / the doll at the crack
        if (ClientState.scareTicks >= 0) {
            g.flush();
            int k = ClientState.scareTicks;
            if (ClientState.scareKind == 2 && HitoriConfig.jumpscares() && k < 10) {
                g.fill(0, 0, w, h, 0xFF000000);
                float zoom = 1.0f + (k + partial) * (calm ? 0.004f : 0.03f);
                int size = (int) (Math.max(w, h) * zoom);
                if (Tex.has(JS_WOMAN)) image(g, JS_WOMAN, (w - size) / 2, (h - size) / 2, size, size, 256, 256, 1, 1, 1, 1);
            } else if (ClientState.scareKind == 1 && k < 14) {
                g.fill(0, 0, w, h, argb(0.35f * (1.0f - k / 14.0f), 0x200000));
            }
        }
        // the fade, the morning
        if (ClientState.fadeTicks >= 0 || ClientState.morningTicks >= 0) g.flush();
        if (ClientState.fadeTicks >= 0) {
            float k = ClientState.fadeTicks + partial;
            int L = ClientState.fadeLength;
            float a = k < L * 0.5f ? k / (L * 0.5f) : k < L + 20 ? 1.0f : Math.max(0.0f, 1.0f - (k - L - 20) / L);
            g.fill(0, 0, w, h, argb(a, 0x000000));
        }
        if (ClientState.morningTicks >= 0) {
            float k = ClientState.morningTicks + partial;
            float a = Mth.clamp(k / 100.0f, 0.0f, 1.0f);
            if (ClientState.morningKind == 0) g.fillGradient(0, 0, w, h, argb(a * 0.85f, 0xFFF4E8), argb(a, 0xFFFFFF));
            else g.fillGradient(0, 0, w, h, argb(a * 0.6f, 0x8890A0), argb(a * 0.9f, 0xB8C0C8));
        }
        // the map (外へ): you are inside 203
        if (ClientState.mapTicks >= 0) map(g, font, w, h, ClientState.mapTicks + partial);
        // found
        if (ClientState.jumpscareTicks >= 0 || ClientState.caughtTicks >= 0 || ClientState.introTicks >= 0) g.flush();
        if (ClientState.jumpscareTicks >= 0) {
            int k = ClientState.jumpscareTicks;
            g.fill(0, 0, w, h, 0xFF000000);
            ResourceLocation face = ClientState.jumpscareKind == 1 ? JS_WOMAN : JS_DOLL;
            if (HitoriConfig.jumpscares() && k < 14) {
                float zoom = 1.0f + (k + partial) * (calm ? 0.004f : 0.04f);
                int size = (int) (Math.max(w, h) * zoom);
                int sx = calm ? 0 : RAND.nextInt(17) - 8, sy = calm ? 0 : RAND.nextInt(17) - 8;
                float flick = calm || k > 3 || k % 2 == 0 ? 1.0f : 0.55f;
                if (Tex.has(face)) image(g, face, (w - size) / 2 + sx, (h - size) / 2 + sy, size, size, 256, 256, flick, flick, flick, 1.0f);
                else centered(g, font, "鬼", w / 2 + sx, h / 2 - 30 + sy, 0xFFE8E8E8, 8.0f);
                if (!calm && k < 2) g.fill(0, 0, w, h, 0x50FFFFFF);
            }
        }
        if (ClientState.caughtTicks >= 0) g.fill(0, 0, w, h, 0xFF000000);
        if (ClientState.introTicks >= 0) intro(g, font, w, h, ClientState.introTicks + partial);
    }

    /** The phone's map: the blue dot sits inside 203 while you walk the corridor. */
    private static void map(GuiGraphics g, Font font, int w, int h, float k) {
        float a = Mth.clamp(k / 15.0f, 0.0f, 1.0f);
        int pw = 150, ph = 210;
        int x0 = w - pw - 20, y0 = h / 2 - ph / 2;
        g.fill(x0 - 4, y0 - 4, x0 + pw + 4, y0 + ph + 4, argb(a, 0x0C0D10));
        g.fill(x0, y0, x0 + pw, y0 + ph, argb(a, 0xE8E4DA));
        // streets, the building, the units
        g.fill(x0, y0 + 120, x0 + pw, y0 + 132, argb(a, 0xFFFFFF));
        g.fill(x0 + 60, y0, x0 + 72, y0 + ph, argb(a, 0xFFFFFF));
        g.fill(x0 + 18, y0 + 70, x0 + 132, y0 + 108, argb(a, 0xC8C0B0));
        for (int i = 1; i < 4; i++) g.fill(x0 + 18 + i * 28, y0 + 70, x0 + 19 + i * 28, y0 + 108, argb(a, 0xA09888));
        int dx = x0 + 18 + 2 * 28 + 14, dy = y0 + 89;
        float pulse = 3.0f + 2.0f * (float) Math.abs(Math.sin(k * 0.2f));
        g.fill((int) (dx - pulse - 2), (int) (dy - pulse - 2), (int) (dx + pulse + 2), (int) (dy + pulse + 2), argb(a * 0.3f, 0x3A7AE8));
        g.fill(dx - 3, dy - 3, dx + 3, dy + 3, argb(a, 0x2A6AE0));
        g.drawString(font, I18n.get("phone.hitori.map_title"), x0 + 6, y0 + 6, argb(a, 0x303030), false);
        g.drawString(font, I18n.get("phone.hitori.map_here"), x0 + 6, y0 + ph - 14, argb(a, 0x2A6AE0), false);
    }

    private static void intro(GuiGraphics g, Font font, int w, int h, float k) {
        int len = ClientState.introLength();
        float black = k < len - 40 ? 1.0f : Math.max(0.0f, 1.0f - (k - (len - 40)) / 40.0f);
        if (black > 0.0f) g.fill(0, 0, w, h, argb(black, 0x000000));
        if (!ClientState.introShort) {
            String[] keys = {"intro.hitori.1", "intro.hitori.2", "intro.hitori.3"};
            for (int i = 0; i < keys.length; i++) {
                float start = 10 + i * 55, l = 60;
                float local = k - start;
                if (local < 0 || local > l) continue;
                float a = local < 12 ? local / 12.0f : local > l - 12 ? (l - local) / 12.0f : 1.0f;
                String[] lines = I18n.get(keys[i]).split("\n");
                int y = h / 2 - lines.length * 7;
                for (String line : lines) {
                    centered(g, font, line, w / 2, y, argb(a, 0xC8C0B4), 1.25f);
                    y += 15;
                }
            }
        }
        float lt = k - (ClientState.introShort ? 5 : 175);
        if (lt >= 0 && lt < 85) {
            float a = lt < 15 ? lt / 15.0f : lt > 55 ? (85 - lt) / 30.0f : 1.0f;
            int lw = Math.min(w * 3 / 5, 384), lh = lw / 2;
            if (Tex.has(LOGO)) {
                image(g, LOGO, (w - lw) / 2, (h - lh) / 2 - 16, lw, lh, 512, 256, 1, 1, 1, a);
            } else {
                centered(g, font, "ひとりかくれんぼ", w / 2, h / 2 - 34, argb(a, 0xE8E0D8), 3.6f);
                centered(g, font, "― HITORI KAKURENBO ―", w / 2, h / 2 + 8, argb(a * 0.8f, 0xB01818), 1.4f);
            }
        }
    }
}
