package com.eternalrealm.ui.chat;

import com.eternalrealm.core.ModInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The small chat's frame pieces ({@code textures/gui/chat/frame.png}, drawn by {@code tools/art/chat_frame.py}; the
 * constants follow its ATLAS): a gold band with a sapphire at each corner, a jewelled crest for the top edge and the
 * frame of the line being typed. Pixel art, one texel per GUI unit.
 */
public final class ChatArt {
    public static final ResourceLocation TEXTURE = new ResourceLocation(ModInfo.MOD_ID, "textures/gui/chat/frame.png");
    private static final int TEX = 64;
    private static final int PANEL_U = 0;
    private static final int PANEL_V = 0;
    private static final int PANEL_SIZE = 32;
    private static final int CORNER = 8;
    private static final int CREST_U = 32;
    private static final int CREST_V = 0;
    public static final int CREST_WIDTH = 32;
    public static final int CREST_HEIGHT = 10;
    private static final int INPUT_U = 0;
    private static final int INPUT_V = 32;
    private static final int INPUT_WIDTH = 32;
    public static final int INPUT_HEIGHT = 16;
    private static final int CAP = 4;
    /** The panel's background: deep night blue, a little lighter at the top. */
    private static final int BACK_TOP = 0x141A30;
    private static final int BACK_BOTTOM = 0x0B0E1C;

    private ChatArt() {
    }

    /** The night-blue ground inside a frame at {@code x, y, width, height}, at {@code alpha} (0..1) of its usual. */
    public static void back(GuiGraphics graphics, int x, int y, int width, int height, float alpha, int baseAlpha) {
        int a = Math.round(baseAlpha * Math.max(0.0F, Math.min(1.0F, alpha)));
        if (a <= 0 || width <= 4 || height <= 4) {
            return;
        }
        graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 2, a << 24 | BACK_TOP, a << 24 | BACK_BOTTOM);
    }

    /** The gold frame (nine-slice) around {@code x, y, width, height}. */
    public static void frame(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        if (alpha <= 0.01F || width < 2 * CORNER || height < 2 * CORNER) {
            return;
        }
        begin(graphics, alpha);
        int inner = PANEL_SIZE - 2 * CORNER;
        int midW = width - 2 * CORNER;
        int midH = height - 2 * CORNER;
        int right = x + width - CORNER;
        int bottom = y + height - CORNER;
        int far = PANEL_SIZE - CORNER;
        graphics.blit(TEXTURE, x, y, PANEL_U, PANEL_V, CORNER, CORNER, TEX, TEX);
        graphics.blit(TEXTURE, right, y, PANEL_U + far, PANEL_V, CORNER, CORNER, TEX, TEX);
        graphics.blit(TEXTURE, x, bottom, PANEL_U, PANEL_V + far, CORNER, CORNER, TEX, TEX);
        graphics.blit(TEXTURE, right, bottom, PANEL_U + far, PANEL_V + far, CORNER, CORNER, TEX, TEX);
        if (midW > 0) {
            graphics.blit(TEXTURE, x + CORNER, y, midW, CORNER, PANEL_U + CORNER, PANEL_V, inner, CORNER, TEX, TEX);
            graphics.blit(TEXTURE, x + CORNER, bottom, midW, CORNER, PANEL_U + CORNER, PANEL_V + far, inner, CORNER,
                    TEX, TEX);
        }
        if (midH > 0) {
            graphics.blit(TEXTURE, x, y + CORNER, CORNER, midH, PANEL_U, PANEL_V + CORNER, CORNER, inner, TEX, TEX);
            graphics.blit(TEXTURE, right, y + CORNER, CORNER, midH, PANEL_U + far, PANEL_V + CORNER, CORNER, inner,
                    TEX, TEX);
        }
        end(graphics);
    }

    /** The crest, centred on {@code centreX}, rising {@code rise} px above the frame's top edge {@code top}. */
    public static void crest(GuiGraphics graphics, int centreX, int top, int rise, float alpha) {
        if (alpha <= 0.01F) {
            return;
        }
        begin(graphics, alpha);
        graphics.blit(TEXTURE, centreX - CREST_WIDTH / 2, top - rise, CREST_U, CREST_V, CREST_WIDTH, CREST_HEIGHT, TEX,
                TEX);
        end(graphics);
    }

    /** The frame of the typing line ({@value #INPUT_HEIGHT} px tall) from {@code x} to {@code x + width}. */
    public static void input(GuiGraphics graphics, int x, int y, int width) {
        if (width < 2 * CAP) {
            return;
        }
        begin(graphics, 1.0F);
        graphics.blit(TEXTURE, x, y, INPUT_U, INPUT_V, CAP, INPUT_HEIGHT, TEX, TEX);
        graphics.blit(TEXTURE, x + CAP, y, width - 2 * CAP, INPUT_HEIGHT, INPUT_U + CAP, INPUT_V,
                INPUT_WIDTH - 2 * CAP, INPUT_HEIGHT, TEX, TEX);
        graphics.blit(TEXTURE, x + width - CAP, y, INPUT_U + INPUT_WIDTH - CAP, INPUT_V, CAP, INPUT_HEIGHT, TEX, TEX);
        end(graphics);
    }

    private static void begin(GuiGraphics graphics, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
    }

    private static void end(GuiGraphics graphics) {
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
