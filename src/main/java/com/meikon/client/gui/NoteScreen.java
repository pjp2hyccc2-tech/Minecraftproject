package com.meikon.client.gui;

import com.meikon.Meikon;
import com.meikon.game.Notes;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** Reading a note on old washi paper. The world does not pause while you read. */
public class NoteScreen extends Screen {
    private static final ResourceLocation PAPER = Meikon.id("textures/gui/paper.png");
    private final int id;
    private final String extra;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int visible;
    private int pw, ph, px, py;

    public NoteScreen(int id, String extra) {
        super(Component.translatable("note.meikon." + id + ".title"));
        this.id = id;
        this.extra = extra == null ? "" : extra;
    }

    public static Component candleText(String extra) {
        String[] parts = extra.split(",");
        Component out = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out = Component.empty().append(out).append(" → ");
            out = Component.empty().append(out).append(Component.translatable("candle.meikon." + parts[i].trim()));
        }
        return out;
    }

    @Override
    protected void init() {
        pw = Math.min(280, width - 30);
        ph = Math.min(height - 24, 300);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        Object a1 = id == Notes.CANDLES ? Minecraft.getInstance().getUser().getName() : extra;
        Object a2 = id == Notes.CANDLES ? candleText(extra) : Component.literal("");
        Component body = Component.translatable("note.meikon." + id + ".body", a1, a2);
        lines = font.split(body, pw - 48);
        visible = Math.max(1, (ph - 70) / 11);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        RenderSystem.enableBlend();
        g.blit(PAPER, px, py, pw, ph, 0, 0, 256, 256, 256, 256);
        RenderSystem.disableBlend();
        g.drawCenteredString(font, title, width / 2, py + 22, 0x3A0E0E);
        g.fill(px + 30, py + 34, px + pw - 30, py + 35, 0x553A2A1A);
        int y = py + 44;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            g.drawString(font, lines.get(i), px + 24, y, 0x2B1D12, false);
            y += 11;
        }
        if (lines.size() > visible) {
            g.drawCenteredString(font, Component.translatable("gui.meikon.scroll", scroll + 1, Math.max(1, lines.size() - visible + 1)),
                    width / 2, py + ph - 18, 0x6A5442);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta) * 2, 0, Math.max(0, lines.size() - visible));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (lines.size() > visible && scroll < lines.size() - visible) {
            scroll = Math.min(lines.size() - visible, scroll + visible - 1);
            return true;
        }
        onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
