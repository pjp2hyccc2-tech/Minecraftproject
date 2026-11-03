package com.hitori.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.hitori.Hitori;
import com.hitori.client.HorrorOverlay;
import com.hitori.client.Tex;
import com.hitori.network.C2S;
import com.hitori.network.ModNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Step 10 (docs/DESIGN.md §1): name the doll — a small text field at the low table; 「みーちゃん」 unless you say otherwise. */
public class NamingScreen extends Screen {
    private static final ResourceLocation FIELD = Hitori.id("textures/gui/name_field.png");
    private final String initial;
    private EditBox box;
    private int ticks;
    private boolean sent;

    public NamingScreen(String initial) {
        super(Component.translatable("gui.hitori.naming"));
        this.initial = initial == null || initial.isEmpty() ? "みーちゃん" : initial;
    }

    @Override
    protected void init() {
        int bw = 160;
        box = new EditBox(font, width / 2 - bw / 2, height / 2 - 6, bw, 18, Component.translatable("gui.hitori.naming"));
        box.setMaxLength(12);
        box.setValue(initial);
        box.setBordered(!Tex.has(FIELD));
        box.setTextColor(0xFFF0E8E0);
        addRenderableWidget(box);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.translatable("gui.hitori.naming_ok"), b -> confirm())
                .bounds(width / 2 - 40, height / 2 + 22, 80, 20).build());
    }

    @Override
    public void tick() {
        ticks++;
        box.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, HorrorOverlay.argb(Math.min(0.75f, (ticks + partial) / 30.0f), 0x020202));
        HorrorOverlay.centered(g, font, I18n.get("gui.hitori.naming_title"), width / 2, height / 2 - 40, 0xFFE8E0D8, 1.4f);
        HorrorOverlay.centered(g, font, I18n.get("gui.hitori.naming_sub"), width / 2, height / 2 - 22, 0xFF908880, 1.0f);
        if (Tex.has(FIELD)) {
            RenderSystem.enableBlend();
            g.blit(FIELD, width / 2 - 96, height / 2 - 16, 192, 36, 0, 0, 192, 36, 192, 36);
            RenderSystem.disableBlend();
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private void confirm() {
        if (sent) return;
        sent = true;
        ModNetwork.sendToServer(new C2S.Name(box.getValue()));
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** CI: the name as typed. */
    public void confirmForTest() {
        confirm();
    }
}
