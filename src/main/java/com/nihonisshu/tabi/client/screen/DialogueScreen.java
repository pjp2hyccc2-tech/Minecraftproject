package com.nihonisshu.tabi.client.screen;

import com.nihonisshu.tabi.network.TabiNet;
import com.nihonisshu.tabi.registry.TabiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Conversation window at the bottom of the screen: name plate, typewriter text with little voice blips,
 * click / space to advance, choices on the last line.
 */
public class DialogueScreen extends Screen {
    private final CompoundTag data;
    private final List<String> lines = new ArrayList<>();
    private final List<String> choices = new ArrayList<>();
    private final RandomSource rand = RandomSource.create();
    private int line;
    private float shown;
    private int ticks;
    private int hoverChoice = -1;
    private boolean sent;

    public DialogueScreen(CompoundTag data) {
        super(Component.literal(data.getString("name")));
        this.data = data;
        ListTag l = data.getList("lines", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) lines.add(l.getString(i));
        if (lines.isEmpty()) lines.add("……");
        ListTag c = data.getList("choices", Tag.TAG_STRING);
        for (int i = 0; i < c.size(); i++) choices.add(c.getString(i));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String current() {
        return lines.get(line);
    }

    private boolean lineDone() {
        return shown >= current().length();
    }

    private boolean onLast() {
        return line >= lines.size() - 1;
    }

    private boolean showingChoices() {
        return onLast() && lineDone() && !choices.isEmpty();
    }

    private float pitch() {
        return switch (data.getString("voice")) {
            case "low" -> 0.72f;
            case "old" -> 0.86f;
            case "high" -> 1.22f;
            case "kid" -> 1.42f;
            default -> 1.0f;
        };
    }

    @Override
    public void tick() {
        ticks++;
        if (!lineDone()) {
            int before = (int) shown;
            shown = Math.min(current().length(), shown + 1.0f);
            int after = (int) shown;
            for (int i = before; i < after; i++) {
                char ch = current().charAt(i);
                if (i % 2 == 0 && !isQuiet(ch)) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(TabiSounds.TALK.get(),
                            pitch() * (0.94f + rand.nextFloat() * 0.12f), 0.28f));
                }
            }
        }
    }

    private static boolean isQuiet(char ch) {
        return ch == ' ' || ch == '　' || ch == '、' || ch == '。' || ch == '…' || ch == '！' || ch == '？' || ch == '「' || ch == '」' || ch == '（' || ch == '）';
    }

    private void advance() {
        if (!lineDone()) {
            shown = current().length();
            return;
        }
        if (!onLast()) {
            line++;
            shown = 0;
            return;
        }
        if (choices.isEmpty()) onClose();
    }

    private void choose(int i) {
        if (sent || i < 0 || i >= choices.size()) return;
        sent = true;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        CompoundTag t = new CompoundTag();
        t.putString("profile", data.getString("profile"));
        t.putString("entry", data.getString("entry"));
        t.putString("name", data.getString("name"));
        t.putInt("entity", data.getInt("entity"));
        t.putInt("choice", i);
        t.putBoolean("shopChoice", data.getBoolean("shopChoice"));
        onClose();
        TabiNet.toServer("choice", t);
    }

    // ---- layout ----
    private int boxW() { return Math.min(440, width - 24); }
    private int boxH() { return 70; }
    private int boxX() { return (width - boxW()) / 2; }
    private int boxY() { return height - boxH() - 14; }

    private int choiceW() {
        int w = 120;
        for (String c : choices) w = Math.max(w, font.width(c) + 34);
        return Math.min(w, boxW());
    }

    private int choiceX() { return boxX() + boxW() - choiceW(); }
    private int choiceY(int i) { return boxY() - 12 - (choices.size() - i) * 20; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, height - 120, width, height, 0x00000000, 0x88000000);
        int x = boxX(), y = boxY(), w = boxW(), h = boxH();
        // washi paper box with indigo frame
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF1F2F4F);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFFC9B98F);
        g.fill(x, y, x + w, y + h, 0xF6F6F0E2);
        for (int i = 0; i < 6; i++) g.fill(x, y + 10 + i * 10, x + w, y + 11 + i * 10, 0x0C6B5A3A);
        // name plate
        String name = title.getString();
        if (!name.isEmpty()) {
            int nw = font.width(name) + 18;
            g.fill(x + 10, y - 15, x + 10 + nw, y + 2, 0xFF1F2F4F);
            g.fill(x + 11, y - 14, x + 9 + nw, y + 1, 0xFF2E4A7D);
            g.drawString(font, name, x + 19, y - 10, 0xFFFFFFFF, false);
        }
        // text
        String cur = current().substring(0, Math.min(current().length(), (int) shown));
        int ty = y + 12;
        for (FormattedCharSequence s : font.split(Component.literal(cur), w - 28)) {
            g.drawString(font, s, x + 14, ty, 0xFF2A2219, false);
            ty += 12;
            if (ty > y + h - 10) break;
        }
        // page counter + advance mark
        if (lines.size() > 1) {
            String pg = (line + 1) + "/" + lines.size();
            g.drawString(font, pg, x + w - 8 - font.width(pg), y + h - 11, 0xFF8A7A5A, false);
        }
        if (lineDone() && !showingChoices()) {
            int by = y + h - 12 + ((ticks / 6) % 2);
            g.drawString(font, "▼", x + w - 34, by, 0xFF2E4A7D, false);
        }
        // choices
        hoverChoice = -1;
        if (showingChoices()) {
            int cw = choiceW(), cx = choiceX();
            for (int i = 0; i < choices.size(); i++) {
                int cy = choiceY(i);
                boolean hover = mx >= cx && mx < cx + cw && my >= cy && my < cy + 18;
                if (hover) hoverChoice = i;
                g.fill(cx - 1, cy - 1, cx + cw + 1, cy + 19, 0xFF1F2F4F);
                g.fill(cx, cy, cx + cw, cy + 18, hover ? 0xFFFFF4D0 : 0xFFF6F0E2);
                g.drawString(font, (i + 1) + ".", cx + 6, cy + 5, 0xFF8A7A5A, false);
                g.drawString(font, choices.get(i), cx + 20, cy + 5, hover ? 0xFFB0381E : 0xFF2A2219, false);
            }
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (showingChoices()) {
            if (hoverChoice >= 0) choose(hoverChoice);
            return true;
        }
        advance();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (showingChoices()) {
            if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
                choose(key - GLFW.GLFW_KEY_1);
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_E) {
            advance();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
