package com.reisatsu.client.gui;

import com.reisatsu.client.PhotoView;
import com.reisatsu.entity.SpiritKind;
import com.reisatsu.game.GameData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/** One photo, large (from the album or an evidence photo in hand). */
public class PhotoScreen extends Screen {
    private final String id;
    private final int result;
    @Nullable private final SpiritKind spirit;
    private final int stars;
    @Nullable private final Screen parent;

    public PhotoScreen(String id, int result, @Nullable SpiritKind spirit, int stars, @Nullable Screen parent) {
        super(Component.translatable("gui.reisatsu.photo"));
        this.id = id;
        this.result = result;
        this.spirit = spirit;
        this.stars = stars;
        this.parent = parent;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int h = (int) (height * 0.84f);
        int w = h * 128 / 160;
        PhotoView.drawPhoto(g, font, (width - w) / 2, (height - h) / 2, h, id, result, spirit, stars, 1.0f, 1.0f);
        String caption = result == GameData.RESULT_CAPTURED && spirit != null ? I18n.get(spirit.roleKey())
                : result == GameData.RESULT_FAILED ? I18n.get("gui.reisatsu.photo_failed") : I18n.get("gui.reisatsu.photo_empty");
        g.drawCenteredString(font, caption, width / 2, (height + h) / 2 + 4, 0xA0D0C8B8);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
