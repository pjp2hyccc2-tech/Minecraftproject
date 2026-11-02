package com.sounan.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.sounan.Sounan;
import com.sounan.client.ClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/** Reading a damp note by flashlight. The world does not pause while you read. */
public class NoteScreen extends Screen {
    private static final ResourceLocation PAPER = Sounan.id("textures/gui/paper.png");
    private final int id;
    private final String name;
    private final String captures;
    private final String date;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int visible;
    private int pw, ph, px, py;

    /** {@code extra} = "playerName|captures|dayOffset". */
    public NoteScreen(int id, String extra) {
        super(Component.translatable("note.sounan." + id + ".title"));
        this.id = id;
        String[] p = (extra == null ? "" : extra).split("\\|");
        this.name = p.length > 0 ? p[0] : "";
        this.captures = p.length > 1 ? p[1] : "0";
        int days = 0;
        try {
            days = p.length > 2 ? Integer.parseInt(p[2]) : 0;
        } catch (NumberFormatException ignored) {
        }
        int[] d = ClientState.dateAfter(days);
        this.date = d[0] + "." + d[1] + "." + d[2];
        ClientState.notesRead |= 1 << Math.min(30, id);
    }

    @Override
    protected void init() {
        pw = Math.min(290, width - 30);
        ph = Math.min(height - 24, 310);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        Component body = Component.translatable("note.sounan." + id + ".body", name, captures, date);
        lines = font.split(body, pw - 48);
        visible = Math.max(1, (ph - 70) / 11);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        RenderSystem.enableBlend();
        g.setColor(0.86f, 0.84f, 0.80f, 1.0f);
        g.blit(PAPER, px, py, pw, ph, 0, 0, 256, 256, 256, 256);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        g.drawCenteredString(font, title, width / 2, py + 22, 0x2A1810);
        g.fill(px + 30, py + 34, px + pw - 30, py + 35, 0x553A2A1A);
        int y = py + 44;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            g.drawString(font, lines.get(i), px + 24, y, 0x22180F, false);
            y += 11;
        }
        if (lines.size() > visible) {
            g.drawCenteredString(font, Component.translatable("gui.sounan.scroll", scroll + 1, Math.max(1, lines.size() - visible + 1)),
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
