package com.reisatsu.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reisatsu.Reisatsu;
import com.reisatsu.client.Tex;
import com.reisatsu.game.Notes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** Reading a note (docs/DESIGN.md §3.11). The report lists the phenomena, ticking the ones you witnessed yourself. */
public class NoteScreen extends Screen {
    private static final ResourceLocation PAPER = Reisatsu.id("textures/gui/paper.png");
    private final String id;
    private final String extra;
    @Nullable private final Screen parent;
    private List<FormattedCharSequence> lines = List.of();
    private int scroll;
    private int visible;
    private int pw, ph, px, py;

    public NoteScreen(String id, String extra) {
        this(id, extra, null);
    }

    public NoteScreen(String id, String extra, @Nullable Screen parent) {
        super(Component.translatable("note.reisatsu." + id + ".title"));
        this.id = id;
        this.extra = extra == null ? "" : extra;
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(300, width - 30);
        ph = Math.min(height - 24, 320);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        List<FormattedCharSequence> out = new ArrayList<>(font.split(Component.translatable("note.reisatsu." + id + ".body"), pw - 48));
        if (id.equals(Notes.REPORT)) {
            out.add(FormattedCharSequence.EMPTY);
            out.addAll(font.split(Component.translatable("note.reisatsu.report.list"), pw - 48));
            List<String> seen = List.of(extra.split(","));
            for (String k : Notes.PHENOMENA) {
                String mark = seen.contains(k) ? "☑ " : "☐ ";
                out.addAll(font.split(Component.literal(mark + I18n.get("phenomenon.reisatsu." + k)), pw - 48));
            }
        }
        lines = out;
        visible = Math.max(1, (ph - 70) / 11);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        if (Tex.has(PAPER)) {
            RenderSystem.enableBlend();
            g.blit(PAPER, px, py, pw, ph, 0, 0, 256, 256, 256, 256);
            RenderSystem.disableBlend();
        } else {
            g.fill(px, py, px + pw, py + ph, 0xFFE8E0CC);
            g.fill(px, py, px + pw, py + 2, 0xFFD0C6B0);
        }
        g.drawCenteredString(font, title, width / 2, py + 20, 0x2A1810);
        g.fill(px + 30, py + 32, px + pw - 30, py + 33, 0x553A2A1A);
        int y = py + 42;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            g.drawString(font, lines.get(i), px + 24, y, 0x22180F, false);
            y += 11;
        }
        if (lines.size() > visible) {
            g.drawCenteredString(font, Component.translatable("gui.reisatsu.scroll", scroll + 1, Math.max(1, lines.size() - visible + 1)),
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
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
