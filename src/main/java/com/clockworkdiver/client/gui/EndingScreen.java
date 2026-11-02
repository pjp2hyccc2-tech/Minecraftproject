package com.clockworkdiver.client.gui;

import com.clockworkdiver.client.ClientState;
import com.clockworkdiver.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The ending: the epilogue and staff roll scroll past slowly turning gears while a music box plays.
 * Lines come from {@code story.clockworkdiver.ending.N}; a prefix picks the style
 * ({@code h|} title, {@code s|} heading, {@code c|} cast line, plain text otherwise, empty = gap).
 */
public class EndingScreen extends Screen {
    private record Row(int style, String text) {}

    private static final int TITLE = 0, HEADING = 1, CAST = 2, TEXT = 3, GAP = 4;

    private final boolean fromStory;
    private final List<Row> rows = new ArrayList<>();
    private float scroll;
    private int ticks;
    private boolean fast;
    private int totalHeight;
    private SoundInstance music;
    private BrassButton done;

    public EndingScreen(boolean fromStory) {
        super(Component.translatable("gui.clockworkdiver.ending"));
        this.fromStory = fromStory;
        Language lang = Language.getInstance();
        for (int i = 0; i < 200 && lang.has("story.clockworkdiver.ending." + i); i++) {
            String raw = lang.getOrDefault("story.clockworkdiver.ending." + i);
            if (raw.isBlank()) rows.add(new Row(GAP, ""));
            else if (raw.startsWith("h|")) rows.add(new Row(TITLE, raw.substring(2)));
            else if (raw.startsWith("s|")) rows.add(new Row(HEADING, raw.substring(2)));
            else if (raw.startsWith("c|")) rows.add(new Row(CAST, raw.substring(2)));
            else rows.add(new Row(TEXT, raw));
        }
    }

    @Override
    protected void init() {
        totalHeight = 0;
        for (Row r : rows) totalHeight += rowHeight(r);
        done = addRenderableWidget(new BrassButton(width / 2 - 60, height - 34, 120, 20, Component.translatable("gui.clockworkdiver.ending.done"),
                b -> onClose()));
        done.glow = true;
        done.visible = false;
        if (music == null && minecraft != null) {
            music = SimpleSoundInstance.forUI(ModSounds.MUSIC_BOX.get(), 1.0f, 0.9f);
            minecraft.getSoundManager().play(music);
        }
    }

    private int rowHeight(Row r) {
        return switch (r.style) {
            case TITLE -> 40;
            case HEADING -> 24;
            case GAP -> 18;
            default -> 14 * Math.max(1, font.split(FormattedText.of(r.text), Math.min(width - 60, 360)).size());
        };
    }

    /** CI tour: jump into the roll. */
    public void setScroll(float pixels) {
        scroll = pixels;
    }

    private boolean ended() {
        return scroll > totalHeight + height * 0.35f;
    }

    @Override
    public void tick() {
        ticks++;
        if (!ended()) scroll += fast ? 3.0f : 0.55f;
        done.visible = ended() || ticks > 40 * 20;
        if (ticks % (20 * 30) == 0 && minecraft != null && !ended()) {
            // Loop the music box while the roll lasts.
            music = SimpleSoundInstance.forUI(ModSounds.MUSIC_BOX.get(), 1.0f, 0.9f);
            minecraft.getSoundManager().play(music);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        fast = !fast;
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) {
            if (ended()) onClose();
            else fast = !fast;
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null && music != null) minecraft.getSoundManager().stop(music);
        if (fromStory) {
            ClientState.HINTS.add("hint.clockworkdiver.postgame.0");
            ClientState.HINTS.add("hint.clockworkdiver.postgame.1");
        }
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float t = ticks + partialTick;
        g.fillGradient(0, 0, width, height, 0xFF120A05, 0xFF2A1A0C);
        Gfx.begin(g);
        // Slow gears in the background.
        Gfx.gear(width * 0.15f, height * 0.3f, 70, 82, 36, t * 0.004f, 0x203A2A16, 6, 0);
        Gfx.gear(width * 0.86f, height * 0.72f, 90, 104, 44, -t * 0.003f, 0x203A2A16, 6, 0);
        Gfx.gear(width * 0.80f, height * 0.18f, 40, 48, 20, t * 0.008f, 0x18D9AE55, 4, 0);
        Gfx.gear(width * 0.22f, height * 0.85f, 46, 55, 24, -t * 0.007f, 0x18D9AE55, 4, 0);
        // Drifting motes of light.
        for (int i = 0; i < 24; i++) {
            float x = (i * 97.3f + t * (0.2f + (i % 5) * 0.05f)) % width;
            float y = height - ((i * 53.1f + t * (0.3f + (i % 3) * 0.1f)) % (height + 20));
            Gfx.circle(x, y, 1.0f + (i % 3) * 0.5f, Gfx.withAlpha(0xFFE0A0, 60 + (i % 4) * 30));
        }
        Gfx.end();

        int y = (int) (height - scroll);
        int wrap = Math.min(width - 60, 360);
        for (Row r : rows) {
            int h = rowHeight(r);
            if (y + h >= -40 && y <= height + 40) {
                float fade = Math.min(1.0f, Math.min((y + 20) / 60.0f, (height - y) / 60.0f));
                int a = Math.max(8, (int) (255 * Math.max(0.0f, fade)));
                switch (r.style) {
                    case TITLE -> {
                        g.pose().pushPose();
                        g.pose().translate(width / 2.0f, y + 6, 0.0f);
                        g.pose().scale(2.4f, 2.4f, 1.0f);
                        Component c = Component.literal(r.text);
                        g.drawString(font, c, -font.width(c) / 2, 0, Gfx.withAlpha(0xF0C860, a), true);
                        g.pose().popPose();
                    }
                    case HEADING -> {
                        g.pose().pushPose();
                        g.pose().translate(width / 2.0f, y + 4, 0.0f);
                        g.pose().scale(1.35f, 1.35f, 1.0f);
                        Component c = Component.literal(r.text);
                        g.drawString(font, c, -font.width(c) / 2, 0, Gfx.withAlpha(0xE8B860, a), true);
                        g.pose().popPose();
                    }
                    case CAST -> g.drawCenteredString(font, r.text, width / 2, y + 2, Gfx.withAlpha(0xF4E6C8, a));
                    case TEXT -> {
                        int ly = y + 2;
                        for (FormattedCharSequence seq : font.split(FormattedText.of(r.text), wrap)) {
                            g.drawString(font, seq, width / 2 - font.width(seq) / 2, ly, Gfx.withAlpha(0xD8C8A8, a), true);
                            ly += 14;
                        }
                    }
                    default -> {
                    }
                }
            }
            y += h;
        }
        if (ended()) {
            float a = Math.min(1.0f, (scroll - totalHeight - height * 0.35f) / 60.0f + 0.3f);
            g.pose().pushPose();
            g.pose().translate(width / 2.0f, height * 0.38f, 0.0f);
            g.pose().scale(2.0f, 2.0f, 1.0f);
            Component c = Component.translatable("gui.clockworkdiver.ending.thanks");
            g.drawString(font, c, -font.width(c) / 2, 0, Gfx.withAlpha(0xF0C860, Math.max(8, (int) (255 * a))), true);
            g.pose().popPose();
        } else {
            g.drawString(font, Component.translatable("gui.clockworkdiver.ending.skip"), 6, height - 12, 0x60E0D0B0, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
