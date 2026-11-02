package com.clockworkdiver.client.hud;

import com.clockworkdiver.client.ClientSetup;
import com.clockworkdiver.client.ClientState;
import com.clockworkdiver.client.gui.ClockArt;
import com.clockworkdiver.client.gui.Gfx;
import com.clockworkdiver.combat.Quality;
import com.clockworkdiver.combat.Tempo;
import com.clockworkdiver.combat.TickStrike;
import com.clockworkdiver.clock.DiveMods;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.story.StoryQuest;
import com.clockworkdiver.story.StoryQuests;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.List;

/**
 * The HUD: the commission tracker, tutorial hint cards, and inside a clock the escapement metronome
 * (strike when the pendulum crosses the centre jewel), steam gauge, combo counter and screen transitions.
 */
public final class ClockworkHud implements IGuiOverlay {
    public static final ClockworkHud INSTANCE = new ClockworkHud();
    private static final float PI = (float) Math.PI;

    private ClockworkHud() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (!mc.options.hideGui) {
            int bottom = drawObjective(g, mc.font);
            if (ClientState.inDive) {
                drawMetronome(g, mc, partialTick, width);
                drawTimer(g, mc, width);
                drawTwists(g, mc.font, bottom + 3);
                drawSteam(g, mc.font, width, height);
                drawCombo(g, mc.font, width, height);
                drawAbort(g, mc.font, height);
            }
            if (mc.screen == null) drawHint(g, mc.font, width, height);
            drawMemory(g, mc.font, partialTick, width, height);
        }
        drawTransition(g, mc.font, partialTick, width, height);
    }

    // ------------------------------------------------------------------ objective

    /** Draws the objective tracker and returns its bottom edge. */
    private int drawObjective(GuiGraphics g, Font font) {
        if (ClientState.objectiveKey.isEmpty()) return 4;
        Component header = Component.translatable("hud.clockworkdiver.objective").withStyle(ChatFormatting.BOLD);
        Component text = Component.translatable(ClientState.objectiveKey, ClientState.objectiveA, ClientState.objectiveB);
        List<FormattedCharSequence> lines = font.split(text, 200);
        Component sub = null;
        if (!ClientState.objectiveSub.isEmpty()) {
            sub = ClientState.objectiveSub.startsWith("story.clockworkdiver.q")
                    ? Component.translatable("hud.clockworkdiver.story_line", ClientState.objectiveSubA, Component.translatable(ClientState.objectiveSub))
                    : Component.translatable(ClientState.objectiveSub, ClientState.objectiveSubA, ClientState.objectiveSubB);
        }
        int w = 12 + Math.max(font.width(header), lines.stream().mapToInt(font::width).max().orElse(0));
        if (sub != null) w = Math.max(w, 12 + font.width(sub));
        int h = 18 + lines.size() * 10 + (sub != null ? 10 : 0);
        boolean bar = ClientState.objectiveB > 0 && ClientState.objectiveKey.contains(".dive.");
        if (bar) h += 7;
        int x = 4, y = 4;
        Gfx.begin(g);
        int flash = ClientState.objectiveFlash;
        Gfx.rect(x, y, x + w, y + h, Gfx.withAlpha(0x1A0E06, 170));
        Gfx.rect(x, y, x + 2, y + h, flash > 0 && (flash / 3) % 2 == 0 ? 0xFFFFF0C0 : 0xFFD9AE55);
        Gfx.rect(x, y + h - 1, x + w, y + h, 0x80D9AE55);
        if (bar) {
            float p = Math.min(1.0f, ClientState.objectiveA / (float) ClientState.objectiveB);
            int by = y + h - 7 - (sub != null ? 10 : 0);
            Gfx.rect(x + 6, by, x + w - 6, by + 3, 0xFF3A2410);
            Gfx.rect(x + 6, by, x + 6 + (w - 12) * p, by + 3, 0xFFF0C860);
        }
        Gfx.end();
        g.drawString(font, header, x + 6, y + 4, 0xFFF0C860, false);
        int ty = y + 15;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 6, ty, 0xFFF4EAD8, false);
            ty += 10;
        }
        if (sub != null) {
            if (bar) ty += 7;
            g.drawString(font, sub, x + 6, ty, ClientState.objectiveSub.startsWith("story.") ? 0xFFE0B870 : 0xFF9AE6FF, false);
        }
        return y + h;
    }

    // ------------------------------------------------------------------ dive twists

    private void drawTimer(GuiGraphics g, Minecraft mc, int width) {
        if (ClientState.deadline <= 0 || mc.level == null) return;
        long left = Math.max(0, ClientState.deadline - mc.level.getGameTime());
        int secs = (int) Math.ceil(left / 20.0);
        String text = String.format("%d:%02d", secs / 60, secs % 60);
        boolean urgent = secs <= 30;
        boolean blink = secs <= 10 && (mc.level.getGameTime() / 5) % 2 == 0;
        int cx = width / 2 + 70;
        int y = "nest".equals(ClientState.defectType) ? 34 : 10;
        Gfx.begin(g);
        Gfx.rect(cx - 30, y - 3, cx + 30, y + 21, Gfx.withAlpha(0x1A0E06, 180));
        Gfx.rect(cx - 30, y + 20, cx + 30, y + 21, urgent ? 0xFFFF5040 : 0xFFD9AE55);
        Gfx.circle(cx - 20, y + 9, 6, 0xFFD9AE55);
        Gfx.circle(cx - 20, y + 9, 4.6f, 0xFFF4EAD8);
        float a = (float) (left % 1200) / 1200.0f * (float) Math.PI * 2.0f;
        Gfx.line(cx - 20, y + 9, cx - 20 + (float) Math.sin(a) * 4.0f, y + 9 - (float) Math.cos(a) * 4.0f, 1.0f, 0xFF8A1E1E);
        Gfx.end();
        g.pose().pushPose();
        g.pose().translate(cx - 10, y + 3, 0.0f);
        g.pose().scale(1.5f, 1.5f, 1.0f);
        g.drawString(mc.font, text, 0, 0, blink ? 0xFFFFFFFF : urgent ? 0xFFFF7060 : 0xFFF4EAD8, true);
        g.pose().popPose();
    }

    private void drawTwists(GuiGraphics g, Font font, int y) {
        List<String> keys = new java.util.ArrayList<>(DiveMods.keys(ClientState.mods));
        if (ClientState.offset != 0 && !DiveMods.has(ClientState.mods, DiveMods.OFFBEAT)) keys.add("mod.clockworkdiver.offbeat");
        int x = 4;
        for (String key : keys) {
            Component c = Component.translatable(key);
            int w = font.width(c) + 14;
            Gfx.begin(g);
            Gfx.rect(x, y, x + w, y + 12, Gfx.withAlpha(0x3A0E06, 190));
            Gfx.rect(x, y, x + 2, y + 12, 0xFFFF8A40);
            Gfx.end();
            g.drawString(font, c, x + 8, y + 2, 0xFFFFC890, false);
            x += w + 3;
        }
    }

    private void drawMemory(GuiGraphics g, Font font, float partial, int width, int height) {
        if (ClientState.memoryTicks <= 0 || ClientState.memoryKey.isEmpty()) return;
        float age = ClientState.MEMORY_DURATION - ClientState.memoryTicks + partial;
        float a = Math.min(1.0f, Math.min(age / 12.0f, ClientState.memoryTicks / 20.0f));
        List<FormattedCharSequence> lines = font.split(Component.translatable(ClientState.memoryKey), 260);
        int w = 284, h = 30 + lines.size() * 11;
        int x = (width - w) / 2, y = height / 2 - 40 - h;
        Gfx.begin(g);
        Gfx.alphaMul = a;
        Gfx.circleGradient(width / 2.0f, y + h / 2.0f, w * 0.7f, 0x508FE8FF, 0x008FE8FF);
        Gfx.brassFrame(x, y, x + w, y + h, 3, 0xF0F2EAD6);
        Gfx.gear(x + 12, y + 11, 3.5f, 5.0f, 8, age * 0.08f, 0xFF4AA8C8, 0, 0);
        Gfx.alphaMul = 1.0f;
        Gfx.end();
        int alpha = Math.max(8, (int) (255 * a));
        Component header = Component.translatable("hud.clockworkdiver.memory", ClientState.memoryIndex, ClientState.memoryCount);
        g.drawString(font, header, x + 22, y + 7, Gfx.withAlpha(0x2A6A8A, alpha), false);
        int ty = y + 21;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 12, ty, Gfx.withAlpha(0x2E1C0C, alpha), false);
            ty += 11;
        }
    }

    // ------------------------------------------------------------------ hints

    private void drawHint(GuiGraphics g, Font font, int width, int height) {
        String key = ClientState.currentHint;
        if (key == null || ClientState.hintTicks <= 0) return;
        int age = ClientState.HINT_DURATION - ClientState.hintTicks;
        float a = Math.min(1.0f, Math.min(age / 8.0f, ClientState.hintTicks / 12.0f));
        List<FormattedCharSequence> lines = font.split(Component.translatable(key), 250);
        int w = 26 + lines.stream().mapToInt(font::width).max().orElse(0);
        int h = 10 + lines.size() * 10;
        int x = (width - w) / 2, y = height - 64 - h - (ClientState.inDive ? 18 : 0);
        Gfx.begin(g);
        Gfx.alphaMul = a;
        Gfx.brassFrame(x, y, x + w, y + h, 3, 0xF0EADCB8);
        Gfx.gear(x + 10, y + h / 2.0f, 3.5f, 5.0f, 8, age * 0.05f, 0xFFB07A28, 0, 0);
        Gfx.alphaMul = 1.0f;
        Gfx.end();
        int ty = y + 6;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x + 19, ty, Gfx.withAlpha(0x2E1C0C, Math.max(8, (int) (255 * a))), false);
            ty += 10;
        }
        if (!ClientState.HINTS.isEmpty()) {
            g.drawString(font, "+" + ClientState.HINTS.size(), x + w - 14, y + h - 9, Gfx.withAlpha(0x8A6A40, Math.max(8, (int) (255 * a))), false);
        }
    }

    // ------------------------------------------------------------------ inside the clock

    private void drawMetronome(GuiGraphics g, Minecraft mc, float partial, int width) {
        long time = mc.level.getGameTime();
        long judged = ClientState.anchor + ClientState.offset;
        boolean offbeat = ClientState.offset != 0;
        double beats = Tempo.beats(time, partial, judged, ClientState.period);
        double dist = Tempo.distanceToBeat(time, partial, judged, ClientState.period);
        boolean perfect = dist <= Tempo.perfectWindow(ClientState.lensLevel);
        float cx = width / 2.0f, py = "nest".equals(ClientState.defectType) ? 30.0f : 6.0f;
        float len = 40.0f;
        float amp = 0.62f;
        float angle = amp * (float) Math.sin(PI * beats);
        float windowAngle = amp * (float) Math.sin(PI * Tempo.perfectWindow(ClientState.lensLevel) / ClientState.period);

        Gfx.begin(g);
        Gfx.arc(cx, py, len - 7, len + 5, PI / 2.0f - amp - 0.08f, PI / 2.0f + amp + 0.08f, 0xB01A0E06);
        Gfx.arc(cx, py, len - 6, len + 4, PI / 2.0f - windowAngle, PI / 2.0f + windowAngle, perfect ? 0xFFFFE27A : 0xC0C08A2E);
        for (int i = -4; i <= 4; i++) {
            float a = PI / 2.0f + i * amp / 4.0f;
            Gfx.line(cx + (float) Math.cos(a) * (len + 1), py + (float) Math.sin(a) * (len + 1), cx + (float) Math.cos(a) * (len + 4),
                    py + (float) Math.sin(a) * (len + 4), 1.0f, 0xFFD9AE55);
        }
        float bx = cx + (float) Math.sin(angle) * len, by = py + (float) Math.cos(angle) * len;
        Gfx.line(cx, py, bx, by, 1.6f, offbeat ? 0xFFC8A0FF : 0xFFE8C46A);
        Gfx.circle(cx, py, 3.0f, 0xFFD9AE55);
        Gfx.circleGradient(bx, by, 5.5f, offbeat ? 0xFFF0E0FF : 0xFFFFF0B0, offbeat ? 0xFF8A4AC0 : 0xFFC08A2E);
        float gem = perfect ? 6.0f : 4.0f;
        Gfx.circle(cx, py + len + 12, gem + 1.5f, 0xFF1A0E06);
        Gfx.circleGradient(cx, py + len + 12, gem, perfect ? 0xFFFFFFFF : 0xFFFF6A7A, perfect ? 0xFFFF2A4A : 0xFF8A1020);
        if (perfect) Gfx.circleGradient(cx, py + len + 12, 16.0f, 0x60FFE27A, 0x00FFE27A);
        Gfx.end();
        if (offbeat) {
            Component label = Component.translatable("hud.clockworkdiver.offbeat");
            g.drawString(mc.font, label, (int) cx - mc.font.width(label) / 2, (int) (py + len + 22), 0xFFD8B0FF, true);
        }
    }

    private void drawSteam(GuiGraphics g, Font font, int width, int height) {
        float cx = width / 2.0f + 91 + 30, cy = height - 24;
        float r = 18.0f;
        float frac = ClientState.steam / ClientState.maxSteam;
        boolean burst = ClientState.steam >= TickStrike.BURST_COST;
        Gfx.begin(g);
        Gfx.circle(cx, cy, r + 3, 0xFF1A0E06);
        Gfx.metalRing(cx, cy, r, r + 2.5f, 0xFFC07A3A);
        Gfx.circleGradient(cx, cy, r, 0xFFF8F2E2, 0xFFD8CCB0);
        float a0 = PI * 0.75f, a1 = PI * 2.25f;
        Gfx.arc(cx, cy, r - 5, r - 2, a0, a0 + (a1 - a0) * frac, burst ? 0xFFE06A20 : 0xFF6A9AC0);
        float threshold = a0 + (a1 - a0) * (TickStrike.BURST_COST / ClientState.maxSteam);
        Gfx.line(cx + (float) Math.cos(threshold) * (r - 6), cy + (float) Math.sin(threshold) * (r - 6),
                cx + (float) Math.cos(threshold) * (r - 1), cy + (float) Math.sin(threshold) * (r - 1), 1.2f, 0xFF8A1E1E);
        float needle = a0 + (a1 - a0) * frac;
        Gfx.line(cx, cy, cx + (float) Math.cos(needle) * (r - 4), cy + (float) Math.sin(needle) * (r - 4), 1.4f, 0xFF8A1E1E);
        Gfx.circle(cx, cy, 2.0f, 0xFF3A2410);
        Gfx.end();
        g.drawCenteredString(font, Component.translatable("hud.clockworkdiver.steam"), (int) cx, (int) (cy + 6), 0xFF3A2410);
        g.drawString(font, String.valueOf((int) ClientState.steam), (int) (cx + r + 6), (int) (cy - 4), burst ? 0xFFFFB060 : 0xFFE0E0E0, true);
        if (burst) {
            g.drawString(font, Component.translatable("hud.clockworkdiver.burst_ready"), (int) (cx + r + 6), (int) (cy + 6), 0xFFFFD27A, true);
        }
    }

    private void drawCombo(GuiGraphics g, Font font, int width, int height) {
        int cx = width / 2, cy = height / 2;
        if (ClientState.combo >= 2) {
            g.pose().pushPose();
            g.pose().translate(cx + 22, cy - 18, 0.0f);
            float sc = 1.0f + Math.min(ClientState.combo, 10) * 0.05f;
            g.pose().scale(sc, sc, 1.0f);
            g.drawString(font, Component.translatable("hud.clockworkdiver.combo", ClientState.combo), 0, 0, 0xFFFFD27A, true);
            g.pose().popPose();
        }
        if (ClientState.clickTicks > 0 && ClientState.clickQuality >= 0) {
            Quality q = Quality.byOrdinal(ClientState.clickQuality);
            float a = ClientState.clickTicks / 14.0f;
            int rise = (14 - ClientState.clickTicks);
            String key = switch (q) {
                case PERFECT -> "hud.clockworkdiver.just";
                case GOOD -> "hud.clockworkdiver.good";
                case MISS -> "hud.clockworkdiver.miss";
            };
            int color = switch (q) {
                case PERFECT -> 0xFFE27A;
                case GOOD -> 0xFFFFFF;
                case MISS -> 0x9A9A9A;
            };
            g.pose().pushPose();
            g.pose().translate(cx, cy - 22 - rise, 0.0f);
            float sc = q == Quality.PERFECT ? 1.6f + a * 0.4f : 1.2f;
            g.pose().scale(sc, sc, 1.0f);
            Component text = Component.translatable(key);
            g.drawString(font, text, -font.width(text) / 2, 0, Gfx.withAlpha(color, Math.max(8, (int) (255 * a))), true);
            g.pose().popPose();
        }
    }

    private void drawAbort(GuiGraphics g, Font font, int height) {
        Component text = Component.translatable("hud.clockworkdiver.abort", ClientSetup.ABORT_KEY.getTranslatedKeyMessage());
        g.drawString(font, text, 6, height - 12, 0x90E0D0B0, true);
        if (ClientState.abortHold > 0) {
            float p = Math.min(1.0f, ClientState.abortHold / 30.0f);
            Gfx.begin(g);
            Gfx.rect(6, height - 16, 6 + font.width(text) * p, height - 14, 0xFFFF6A4A);
            Gfx.end();
        }
    }

    // ------------------------------------------------------------------ transitions

    private void drawTransition(GuiGraphics g, Font font, float partial, int width, int height) {
        int kind = ClientState.transitionKind;
        if (kind < 0) return;
        if (kind == S2C.TRANSITION_QUEST || kind == S2C.TRANSITION_CHAPTER || kind == S2C.TRANSITION_CHAPTER_END) {
            // Story cards step aside while a dialogue or other screen is open.
            if (Minecraft.getInstance().screen == null) drawStoryCard(g, font, partial, width, height, kind);
            return;
        }
        float t = ClientState.transitionTicks + partial;
        float dur = ClientState.transitionDuration(kind);
        float fadeIn, overlay;
        int overlayColor;
        switch (kind) {
            case 0 -> { // arrive inside the clock: white fades out
                overlay = 1.0f - Gfx.smoothstep(0.0f, 24.0f, t);
                overlayColor = 0xFFF8F0;
            }
            case 1 -> { // back at normal size
                overlay = 1.0f - Gfx.smoothstep(0.0f, 20.0f, t);
                overlayColor = 0xFFFFFF;
            }
            case 2 -> {
                overlay = 0.55f * (1.0f - Gfx.smoothstep(0.0f, 30.0f, t));
                overlayColor = 0xFFD86A;
            }
            case 3 -> {
                overlay = 0.6f * (1.0f - Gfx.smoothstep(0.0f, 30.0f, t));
                overlayColor = 0x8A1010;
            }
            case 7 -> {
                overlay = 0.5f * (1.0f - Gfx.smoothstep(0.0f, 30.0f, t));
                overlayColor = 0x5A1A8A;
            }
            default -> {
                overlay = 1.0f - Gfx.smoothstep(0.0f, 26.0f, t);
                overlayColor = 0x1A0E06;
            }
        }
        if (overlay > 0.002f) g.fill(0, 0, width, height, Gfx.withAlpha(overlayColor, (int) (255 * overlay)));
        fadeIn = Gfx.smoothstep(6.0f, 16.0f, t) * (1.0f - Gfx.smoothstep(dur - 12.0f, dur, t));
        if (fadeIn <= 0.02f || ClientState.transitionTitle.isEmpty()) return;
        Component title = Component.translatable(ClientState.transitionTitle);
        Component sub = ClientState.transitionSubtitle.isEmpty() ? Component.empty() : Component.translatable(ClientState.transitionSubtitle);
        int alpha = Math.max(8, (int) (255 * fadeIn));
        float scale = kind == 2 ? 3.0f + 0.6f * (1.0f - Gfx.smoothstep(0.0f, 10.0f, t)) : 2.6f;
        int titleColor = kind == 2 ? 0xFFE27A : kind == 3 ? 0xFF8A7A : kind == 7 ? 0xE0B0FF : 0xFFF0D8;
        Gfx.begin(g);
        Gfx.rect(0, height * 0.32f, width, height * 0.32f + 64, Gfx.withAlpha(0x000000, (int) (110 * fadeIn)));
        Gfx.rect(width * 0.2f, height * 0.32f + 44, width * 0.8f, height * 0.32f + 45, Gfx.withAlpha(0xD9AE55, (int) (220 * fadeIn)));
        Gfx.end();
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, height * 0.32f + 8, 0.0f);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, title, -font.width(title) / 2, 0, Gfx.withAlpha(titleColor, alpha), true);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, height * 0.32f + 49, 0.0f);
        g.pose().scale(1.2f, 1.2f, 1.0f);
        g.drawString(font, sub, -font.width(sub) / 2, 0, Gfx.withAlpha(0xE8D8B8, alpha), true);
        g.pose().popPose();
        if (kind == 0) {
            float p = Gfx.smoothstep(10.0f, 40.0f, t);
            Gfx.begin(g);
            for (int i = 0; i < 3; i++) {
                float gx = width / 2.0f + (i - 1) * 26.0f;
                Gfx.gear(gx, height * 0.32f - 16, 6, 8, 10, p * 3.0f * (i % 2 == 0 ? 1 : -1), Gfx.withAlpha(ClockArt.BRASS & 0xFFFFFF, alpha), 0, 0);
            }
            Gfx.end();
        }
    }

    /** Chapter and quest title cards: a parchment banner across the screen. */
    private void drawStoryCard(GuiGraphics g, Font font, float partial, int width, int height, int kind) {
        float t = ClientState.transitionTicks + partial;
        float dur = ClientState.transitionDuration(kind);
        float a = Gfx.smoothstep(0.0f, 14.0f, t) * (1.0f - Gfx.smoothstep(dur - 16.0f, dur, t));
        if (a <= 0.01f) return;
        int alpha = Math.max(8, (int) (255 * a));
        Component small, big, sub;
        if (kind == S2C.TRANSITION_QUEST) {
            StoryQuest q = StoryQuests.byId(ClientState.transitionTitle);
            if (q == null) return;
            small = Component.translatable("hud.clockworkdiver.quest_no", q.number);
            big = Component.translatable("hud.clockworkdiver.quest_title", Component.translatable(q.titleKey()));
            sub = Component.translatable("hud.clockworkdiver.quest_client", Component.translatable(ClientState.transitionSubtitle));
        } else {
            int chapter = parse(ClientState.transitionTitle);
            small = Component.translatable(kind == S2C.TRANSITION_CHAPTER_END ? "hud.clockworkdiver.chapter_end" : "hud.clockworkdiver.chapter_no",
                    Component.translatable("hud.clockworkdiver.chapter_kanji." + chapter));
            big = Component.translatable(ClientState.transitionSubtitle);
            sub = Component.translatable(ClientState.transitionSubtitle + ".sub");
            g.fill(0, 0, width, height, Gfx.withAlpha(0x0A0604, (int) (120 * a)));
        }
        float cy = height * 0.30f;
        float slide = (1.0f - Gfx.smoothstep(0.0f, 14.0f, t)) * 30.0f;
        Gfx.begin(g);
        Gfx.alphaMul = a;
        Gfx.rect(0, cy - 4, width, cy + 62, 0xC0140C06);
        Gfx.rectV(width * 0.12f - slide, cy, width * 0.88f + slide, cy + 58, 0xF0F2E6C8, 0xF0E0CCA4);
        Gfx.rect(width * 0.12f - slide, cy, width * 0.88f + slide, cy + 2, 0xFFD9AE55);
        Gfx.rect(width * 0.12f - slide, cy + 56, width * 0.88f + slide, cy + 58, 0xFFD9AE55);
        for (int side : new int[]{-1, 1}) {
            float gx = width / 2.0f + side * width * 0.33f;
            Gfx.gear(gx, cy + 29, 12, 16, 12, t * 0.03f * side, 0xFF8C6424, 4, 0);
            Gfx.circle(gx, cy + 29, 4, 0xFF3A2410);
        }
        Gfx.alphaMul = 1.0f;
        Gfx.end();
        g.drawCenteredString(font, small, width / 2, (int) cy + 6, Gfx.withAlpha(0x8A5A10, alpha));
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, cy + 18, 0.0f);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, big, -font.width(big) / 2, 0, Gfx.withAlpha(0x2E1C0C, alpha), false);
        g.pose().popPose();
        g.drawCenteredString(font, sub, width / 2, (int) cy + 43, Gfx.withAlpha(0x6A5236, alpha));
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
