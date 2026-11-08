package com.eternalrealm.ui.hud;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.client.ClientCharacterState;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.config.ERClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.Locale;
import java.util.Optional;

/**
 * Basic HUD: level, EXP bar with exact numbers, and unspent stat points with the key that opens the character screen.
 * Displays server snapshots only.
 */
public final class CharacterHudOverlay implements IGuiOverlay {
    private static final int MIN_WIDTH = 120;
    private static final int PADDING = 3;
    private static final int LINE_GAP = 2;
    private static final int BAR_HEIGHT = 5;
    private static final int BACKGROUND = 0x90000000;
    private static final int BAR_BACKGROUND = 0xFF2A2A2A;
    private static final int BAR_FILL = 0xFF4FC3F7;
    private static final int BAR_FILL_MAX = 0xFFFFD54F;
    private static final int TEXT = 0xFFFFFF;
    private static final int TEXT_DIM = 0xD0D0D0;
    private static final int TEXT_LOADING = 0xA0A0A0;
    private static final int TEXT_HIGHLIGHT = 0xFFFF55;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = gui.getMinecraft();
        if (minecraft.options.hideGui || minecraft.options.renderDebug || minecraft.player == null
                || !ERClientConfig.HUD_ENABLED.get()) {
            return;
        }
        Font font = minecraft.font;
        int x = ERClientConfig.HUD_X.get();
        int y = ERClientConfig.HUD_Y.get();

        Optional<CharacterSnapshot> state = ClientCharacterState.snapshot();
        if (state.isEmpty()) {
            Component loading = Component.translatable("hud.eternalrealm.loading");
            graphics.fill(x, y, x + font.width(loading) + PADDING * 2, y + font.lineHeight + PADDING * 2, BACKGROUND);
            graphics.drawString(font, loading, x + PADDING, y + PADDING, TEXT_LOADING);
            return;
        }

        CharacterSnapshot snapshot = state.get();
        Component levelText = snapshot.atCap()
                ? Component.translatable("hud.eternalrealm.level_max", snapshot.level())
                : Component.translatable("hud.eternalrealm.level", snapshot.level());
        Component expText = snapshot.atCap()
                ? Component.translatable("hud.eternalrealm.exp_max")
                : Component.translatable("hud.eternalrealm.exp", format(snapshot.experience()), format(snapshot.experienceToNext()));
        Component pointsText = snapshot.unspentStatPoints() > 0
                ? Component.translatable("hud.eternalrealm.points", snapshot.unspentStatPoints(),
                ClientKeyMappings.OPEN_CHARACTER.getTranslatedKeyMessage())
                : null;

        int contentWidth = Math.max(MIN_WIDTH, Math.max(font.width(levelText), font.width(expText)));
        if (pointsText != null) {
            contentWidth = Math.max(contentWidth, font.width(pointsText));
        }
        int height = PADDING + font.lineHeight + LINE_GAP + BAR_HEIGHT + LINE_GAP + font.lineHeight + PADDING;
        if (pointsText != null) {
            height += LINE_GAP + font.lineHeight;
        }
        graphics.fill(x, y, x + contentWidth + PADDING * 2, y + height, BACKGROUND);

        int left = x + PADDING;
        int cursor = y + PADDING;
        graphics.drawString(font, levelText, left, cursor, TEXT);
        cursor += font.lineHeight + LINE_GAP;

        graphics.fill(left, cursor, left + contentWidth, cursor + BAR_HEIGHT, BAR_BACKGROUND);
        int filled = (int) Math.round(contentWidth * snapshot.progress());
        if (filled > 0) {
            graphics.fill(left, cursor, left + filled, cursor + BAR_HEIGHT, snapshot.atCap() ? BAR_FILL_MAX : BAR_FILL);
        }
        cursor += BAR_HEIGHT + LINE_GAP;

        graphics.drawString(font, expText, left, cursor, TEXT_DIM);
        if (pointsText != null) {
            cursor += font.lineHeight + LINE_GAP;
            graphics.drawString(font, pointsText, left, cursor, TEXT_HIGHLIGHT);
        }
    }

    private static String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
