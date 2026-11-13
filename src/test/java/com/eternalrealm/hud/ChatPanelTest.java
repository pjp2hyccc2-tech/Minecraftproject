package com.eternalrealm.hud;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChatPanelTest {
    @Test
    public void thePanelIsSmallOnEveryScreen() {
        for (int width : new int[]{320, 427, 480, 640, 960, 1280}) {
            int panel = ChatPanel.width(width);
            assertTrue(width + ": " + panel, panel >= ChatPanel.MIN_WIDTH && panel <= ChatPanel.MAX_WIDTH);
            assertTrue("smaller than the vanilla chat (320 + padding): " + panel, panel < 328);
        }
        assertTrue("never wider than a tiny screen", ChatPanel.width(150) <= 146);
    }

    @Test
    public void fiveLinesAreLowerThanTheVanillaChat() {
        int pitch = ChatPanel.pitch(ChatPanel.textScale(1.0), 0.0);
        assertEquals(8, pitch);
        int reserved = ChatPanel.reservedHeight(5, pitch);
        assertTrue("vanilla's unfocused chat is 90 px: " + reserved, reserved < 90);
        assertEquals(ChatPanel.reservedHeight(ChatPanel.MAX_LINES, pitch), ChatPanel.reservedHeight(99, pitch));
        assertEquals(ChatPanel.reservedHeight(ChatPanel.MIN_LINES, pitch), ChatPanel.reservedHeight(0, pitch));
    }

    @Test
    public void textWrapsInsideTheFrame() {
        int wrap = ChatPanel.wrapWidth(200, 0.85);
        assertEquals((int) Math.floor((200 - 2 * (ChatPanel.FRAME + ChatPanel.PADDING)) / 0.85), wrap);
        assertTrue(ChatPanel.textScale(0.1) >= 0.5);
    }

    @Test
    public void messagesStayTenSecondsAndFadeInTheLast() {
        assertEquals(1.0, ChatPanel.fade(0), 1e-9);
        assertEquals(1.0, ChatPanel.fade(ChatPanel.SHOW_TICKS - ChatPanel.FADE_TICKS), 1e-9);
        double fading = ChatPanel.fade(ChatPanel.SHOW_TICKS - ChatPanel.FADE_TICKS / 2);
        assertTrue(fading > 0.0 && fading < 1.0);
        assertEquals(0.0, ChatPanel.fade(ChatPanel.SHOW_TICKS), 1e-9);
        assertEquals(0.0, ChatPanel.fade(10_000), 1e-9);
    }

    @Test
    public void theNewestLinesAreShownBottomFirst() {
        List<ChatPanel.Message> messages = List.of(new ChatPanel.Message(2, 5), new ChatPanel.Message(1, 40),
                new ChatPanel.Message(3, 60), new ChatPanel.Message(1, 500));
        List<ChatPanel.Line> lines = ChatPanel.visible(messages, 5);
        assertEquals(5, lines.size());
        // the newest message's last line is at the bottom
        assertEquals(new ChatPanel.Line(0, 1, 1.0), lines.get(0));
        assertEquals(new ChatPanel.Line(0, 0, 1.0), lines.get(1));
        assertEquals(new ChatPanel.Line(1, 0, 1.0), lines.get(2));
        // a long message that does not fit shows its last lines
        assertEquals(new ChatPanel.Line(2, 2, 1.0), lines.get(3));
        assertEquals(new ChatPanel.Line(2, 1, 1.0), lines.get(4));
    }

    @Test
    public void oldMessagesAreGoneAndThePanelFadesWithTheLast() {
        List<ChatPanel.Message> messages = List.of(new ChatPanel.Message(1, 195), new ChatPanel.Message(2, 300));
        List<ChatPanel.Line> lines = ChatPanel.visible(messages, 5);
        assertEquals(1, lines.size());
        assertEquals(ChatPanel.fade(195), ChatPanel.panelAlpha(lines), 1e-9);
        assertTrue(ChatPanel.visible(List.of(new ChatPanel.Message(3, 200)), 5).isEmpty());
        assertEquals(0.0, ChatPanel.panelAlpha(List.of()), 1e-9);
    }
}
