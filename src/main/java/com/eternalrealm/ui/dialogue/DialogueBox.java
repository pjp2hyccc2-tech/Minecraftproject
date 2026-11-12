package com.eternalrealm.ui.dialogue;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a dialogue: a dark panel with a gold frame, the speaker's portrait on the left, their name on a tab above the
 * panel, the line written out so far, the page number and a blinking "next" arrow once the line is complete.
 */
final class DialogueBox {
    private static final int PANEL = 0xE60E1220;
    private static final int PANEL_AMBIENT = 0xC80E1220;
    private static final int FRAME = 0xFFC9A55A;
    private static final int FRAME_INNER = 0x40FFFFFF;
    private static final int NAME = 0xF2D27A;
    private static final int TEXT = 0xF4F4F8;
    private static final int NARRATION = 0xC4C8D4;
    private static final int HINT = 0x9098A8;
    private static final int LINE_HEIGHT = 11;

    private DialogueBox() {
    }

    /**
     * @param bottom y of the panel's lower edge
     * @param hint   small grey text in the panel's top-right corner (may be null)
     */
    /** Room the name tab and the key hint take above the panel. */
    static final int TAB = 12;

    /** Height of the panel {@link #render} draws for this line (without the name tab above it). */
    static int height(Font font, ClientDialogue.Conversation conversation, int maxWidth, boolean large) {
        ClientDialogue.Line line = conversation.line();
        int portraitSize = large ? 48 : 32;
        boolean portrait = line.portrait() != null;
        int width = Math.min(maxWidth, large ? 420 : 330);
        int pad = large ? 6 : 4;
        int textLeft = pad + (portrait ? portraitSize + pad + 2 : 4);
        int textWidth = width - textLeft - pad - 8;
        int textHeight = wrap(font, line.text().getString(), textWidth).size() * LINE_HEIGHT;
        return Math.max(portrait ? portraitSize + pad * 2 : 0, textHeight + pad * 2 + (large ? 8 : 4));
    }

    static void render(GuiGraphics graphics, Font font, ClientDialogue.Conversation conversation, int centerX, int bottom,
                       int maxWidth, boolean large, Component hint) {
        ClientDialogue.Line line = conversation.line();
        int portraitSize = large ? 48 : 32;
        boolean portrait = line.portrait() != null;
        int width = Math.min(maxWidth, large ? 420 : 330);
        int pad = large ? 6 : 4;
        int textLeft = pad + (portrait ? portraitSize + pad + 2 : 4);
        int textWidth = width - textLeft - pad - 8;
        String full = line.text().getString();
        List<String> wrapped = wrap(font, full, textWidth);
        int textHeight = wrapped.size() * LINE_HEIGHT;
        int height = Math.max(portrait ? portraitSize + pad * 2 : 0, textHeight + pad * 2 + (large ? 8 : 4));
        int left = centerX - width / 2;
        int top = bottom - height;

        // panel and frame
        graphics.fill(left, top, left + width, bottom, large ? PANEL : PANEL_AMBIENT);
        graphics.renderOutline(left, top, width, height, FRAME);
        graphics.renderOutline(left + 1, top + 1, width - 2, height - 2, FRAME_INNER);

        // name tab
        if (line.speaker() != null) {
            int nameWidth = font.width(line.speaker()) + 10;
            int tabTop = top - LINE_HEIGHT - 2;
            graphics.fill(left + 6, tabTop, left + 6 + nameWidth, top, large ? PANEL : PANEL_AMBIENT);
            graphics.renderOutline(left + 6, tabTop, nameWidth, LINE_HEIGHT + 3, FRAME);
            graphics.fill(left + 7, top - 1, left + 5 + nameWidth, top + 1, large ? PANEL : PANEL_AMBIENT);
            graphics.drawString(font, line.speaker(), left + 11, tabTop + 3, NAME, false);
        }

        // portrait
        if (portrait) {
            int px = left + pad + 1;
            int py = top + (height - portraitSize) / 2;
            graphics.fill(px - 1, py - 1, px + portraitSize + 1, py + portraitSize + 1, 0xFF05070C);
            int pixels = Math.max(1, line.portraitPixels());
            graphics.blit(line.portrait(), px, py, portraitSize, portraitSize, 0.0F, 0.0F, pixels, pixels, pixels,
                    pixels);
            graphics.renderOutline(px - 1, py - 1, portraitSize + 2, portraitSize + 2, 0xFF6E5A33);
        }

        // the line, written out so far
        int visible = conversation.visibleChars(full.length());
        int y = top + pad + (large ? 4 : 2);
        int remaining = visible;
        for (String row : wrapped) {
            if (remaining <= 0) {
                break;
            }
            String shown = row.length() <= remaining ? row : row.substring(0, remaining);
            remaining -= row.length();
            Component text = line.narration() ? Component.literal(shown).withStyle(ChatFormatting.ITALIC)
                    : Component.literal(shown);
            graphics.drawString(font, text, left + textLeft, y, line.narration() ? NARRATION : TEXT, false);
            y += LINE_HEIGHT;
        }

        // hint, page, next arrow
        if (hint != null) {
            int hintWidth = font.width(hint);
            graphics.pose().pushPose();
            graphics.pose().translate(left + width - pad - hintWidth * 0.75F, top - 8.0F, 0.0F);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.drawString(font, hint, 0, 0, HINT, true);
            graphics.pose().popPose();
        }
        if (conversation.size() > 1) {
            String page = (conversation.index() + 1) + "/" + conversation.size();
            graphics.drawString(font, page, left + width - pad - 12 - font.width(page), bottom - pad - 8, HINT, false);
        }
        if (visible >= full.length() && (Util.getMillis() / 400L) % 2L == 0L) {
            graphics.drawString(font, "▼", left + width - pad - 8, bottom - pad - 8, NAME, false);
        }
    }

    /** Word-wraps (or, for Japanese, character-wraps) the line to the panel's text width. */
    static List<String> wrap(Font font, String text, int width) {
        List<String> rows = new ArrayList<>();
        for (FormattedText part : font.getSplitter().splitLines(text, Math.max(20, width), Style.EMPTY)) {
            rows.add(part.getString());
        }
        if (rows.isEmpty()) {
            rows.add("");
        }
        return rows;
    }
}
