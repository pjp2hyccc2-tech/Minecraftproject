package com.hitori.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.hitori.Hitori;
import com.hitori.client.ClientState;
import com.hitori.client.HorrorOverlay;
import com.hitori.client.Tex;
import com.hitori.game.Clock;
import com.hitori.game.Step;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import com.hitori.registry.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The printout from the old forum thread (docs/DESIGN.md §1, key J): what you need, the procedure with a checklist
 * (☑ the steps done, the current one marked), the way to end it, and the rules. Three pages; scroll or ←/→.
 */
public class PrintoutScreen extends Screen {
    private static final ResourceLocation PAPER = Hitori.id("textures/gui/printout.png");
    private static final int PW = 256, PH = 320;
    private int page;
    private int ticks;

    public PrintoutScreen() {
        super(Component.translatable("gui.hitori.printout"));
    }

    @Override
    protected void init() {
        ModNetwork.sendToServer(new C2S.Action(C2S.PRINTOUT, 0));
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ITEM_PAPER.get(), 1.0f, 0.6f));
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0xB0000000);
        float scale = Math.min(1.0f, (height - 16) / (float) PH);
        int pw = (int) (PW * scale), ph = (int) (PH * scale);
        int x0 = (width - pw) / 2, y0 = (height - ph) / 2;
        if (Tex.has(PAPER)) {
            RenderSystem.enableBlend();
            g.blit(PAPER, x0, y0, pw, ph, 0, 0, PW, PH, PW, PH);
            RenderSystem.disableBlend();
        } else {
            g.fill(x0 + 3, y0 + 3, x0 + pw + 3, y0 + ph + 3, 0x60000000);
            g.fill(x0, y0, x0 + pw, y0 + ph, 0xFFEDE9DF);
            g.fill(x0, y0, x0 + pw, y0 + 16, 0xFFD8D2C4);
            g.fill(x0 + 6, y0 + 24, x0 + pw - 6, y0 + 25, 0xFFB8B0A0);
        }
        g.pose().pushPose();
        g.pose().translate(x0, y0, 0);
        g.pose().scale(scale, scale, 1.0f);
        int ink = 0xFF2A2622, faint = 0xFF7A7268, red = 0xFFA0201A;
        int x = 12, y = 5;
        g.drawString(font, I18n.get("printout.hitori.site"), x, y, faint, false);
        y = 20;
        y = wrap(g, I18n.get("printout.hitori.title"), x, y, PW - 24, red) + 2;
        g.drawString(font, I18n.get("printout.hitori.post"), x, y, faint, false);
        y += 14;
        switch (page) {
            case 0 -> {
                g.drawString(font, I18n.get("printout.hitori.need"), x, y, ink, false);
                y += 11;
                y = wrap(g, I18n.get("printout.hitori.need_list"), x + 6, y, PW - 30, ink) + 6;
                g.drawString(font, I18n.get("printout.hitori.prep"), x, y, ink, false);
                y += 11;
                int n = 1;
                for (Step s : Step.PREP) y = stepLine(g, s, n++, x, y);
                y += 4;
                wrap(g, I18n.get("printout.hitori.prep_note"), x, y, PW - 24, faint);
            }
            case 1 -> {
                g.drawString(font, I18n.get("printout.hitori.ritual"), x, y, ink, false);
                y += 11;
                int n = 10;
                for (Step s : Step.RITUAL) y = stepLine(g, s, n++, x, y);
                y += 6;
                g.drawString(font, I18n.get("printout.hitori.finish"), x, y, ink, false);
                y += 11;
                for (Step s : Step.FINISH) y = stepLine(g, s, n++, x, y);
            }
            default -> {
                g.drawString(font, I18n.get("printout.hitori.rules"), x, y, red, false);
                y += 13;
                for (int i = 1; i <= 5; i++) y = wrap(g, I18n.get("rule.hitori." + i), x + 4, y, PW - 28, ink) + 4;
                y += 8;
                y = wrap(g, I18n.get("printout.hitori.warning"), x, y, PW - 24, red) + 8;
                wrap(g, I18n.get("printout.hitori.reply"), x, y, PW - 24, faint);
            }
        }
        String pg = (page + 1) + " / 3";
        g.drawString(font, pg, PW - 12 - font.width(pg), PH - 14, faint, false);
        g.pose().popPose();
        String hint = I18n.get("gui.hitori.printout_hint", Clock.hhmm(ClientState.clock));
        g.drawCenteredString(font, hint, width / 2, height - 10, 0x90C0C0C0);
        super.render(g, mouseX, mouseY, partial);
    }

    private int stepLine(GuiGraphics g, Step s, int n, int x, int y) {
        boolean done = ClientState.done(s);
        boolean current = ClientState.hud == s.ordinal();
        String box = done ? "☑" : "☐";
        String text = n + ". " + I18n.get("printout.hitori.step." + s.id);
        int col = done ? 0xFF8A8478 : current ? 0xFFA0201A : 0xFF2A2622;
        if (current && (ticks / 10) % 2 == 0) g.drawString(font, "▶", x - 9, y, 0xFFA0201A, false);
        g.drawString(font, box, x, y, col, false);
        int ny = wrap(g, text, x + 10, y, PW - 34, col);
        if (done) g.fill(x + 10, y + 4, x + 10 + Math.min(PW - 34, font.width(text)), y + 5, 0x808A8478);
        return ny + 1;
    }

    private int wrap(GuiGraphics g, String text, int x, int y, int w, int color) {
        for (String para : text.split("\n")) {
            List<FormattedCharSequence> lines = font.split(Component.literal(para), w);
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, x, y, color, false);
                y += 10;
            }
        }
        return y;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        turn(delta < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        turn(button == 1 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D || key == GLFW.GLFW_KEY_SPACE) {
            turn(1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            turn(-1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_J) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void turn(int d) {
        int np = Math.max(0, Math.min(2, page + d));
        if (np != page && minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ITEM_PAPER.get(), 1.1f, 0.5f));
        page = np;
    }

    public PrintoutScreen page(int p) {
        page = p;
        return this;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static int argb(float a, int rgb) {
        return HorrorOverlay.argb(a, rgb);
    }
}
