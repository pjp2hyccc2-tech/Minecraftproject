package com.eternalrealm.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * The small framed chat (pure): its size for a screen, how long a message stays and how it fades, and which wrapped
 * lines of the latest messages are shown while the player is not typing. The client draws it bottom left where the
 * vanilla chat would be ({@code ui.chat.FantasyChat}); while typing, the vanilla chat comes back inside the same frame.
 */
public final class ChatPanel {
    /** A message stays this long (ticks), like the vanilla chat. */
    public static final int SHOW_TICKS = 200;
    /** ... and fades out over its last second. */
    public static final int FADE_TICKS = 20;
    public static final int MIN_WIDTH = 170;
    public static final int MAX_WIDTH = 240;
    /** The frame's band (px) and the room between it and the text. */
    public static final int FRAME = 4;
    public static final int PADDING = 2;
    /** The crest on the frame's top edge rises this far above it. */
    public static final int CREST_RISE = 4;
    /** The panel's bottom is where the vanilla chat's is: this far above the bottom of the screen. */
    public static final int BOTTOM_OFFSET = 40;
    public static final int LEFT = 2;
    /** The text is this much smaller than the vanilla chat's. */
    public static final double TEXT_SCALE = 0.85;
    public static final int MIN_LINES = 2;
    public static final int MAX_LINES = 10;

    private ChatPanel() {
    }

    /** The panel's width (frame included) on a screen {@code screenWidth} GUI pixels wide. */
    public static int width(int screenWidth) {
        int wanted = (int) Math.round(screenWidth * 0.36);
        int width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, wanted));
        return Math.max(0, Math.min(width, screenWidth - 2 * LEFT));
    }

    /** Width the text wraps at, in the text's own (scaled) pixels. */
    public static int wrapWidth(int panelWidth, double textScale) {
        int inner = panelWidth - 2 * (FRAME + PADDING);
        return Math.max(1, (int) Math.floor(inner / textScale));
    }

    /** The text's scale for the vanilla chat scale option. */
    public static double textScale(double chatScale) {
        return Math.max(0.5, TEXT_SCALE * chatScale);
    }

    /** Distance between two lines (GUI px) for a text scale and the vanilla line spacing option (0..1). */
    public static int pitch(double textScale, double lineSpacing) {
        return Math.max(5, (int) Math.ceil(9.0 * textScale * (1.0 + Math.max(0.0, lineSpacing))));
    }

    /** The panel's height (frame included) for {@code lines} lines. */
    public static int height(int lines, int pitch) {
        return 2 * (FRAME + PADDING) + Math.max(0, lines) * pitch;
    }

    /** How much of the screen the panel may take (for the HUD layout): its height at most, with the crest. */
    public static int reservedHeight(int lines, int pitch) {
        return height(clampLines(lines), pitch) + CREST_RISE;
    }

    public static int clampLines(int lines) {
        return Math.max(MIN_LINES, Math.min(MAX_LINES, lines));
    }

    /** Opacity of a message {@code age} ticks old: 1, then down to 0 over the last second, 0 after. */
    public static double fade(int age) {
        if (age < 0) {
            return 1.0;
        }
        if (age >= SHOW_TICKS) {
            return 0.0;
        }
        double left = (SHOW_TICKS - age) / (double) FADE_TICKS;
        double t = Math.min(1.0, left);
        return t * t;
    }

    /** One message as it is shown: how many lines it wraps to and how old it is (ticks). */
    public record Message(int lines, int age) {
    }

    /** A line to draw: which message (index into the list), which of its lines, and how opaque. */
    public record Line(int message, int line, double alpha) {
    }

    /**
     * The lines to show, bottom first: the newest messages' lines (a long message's last lines when it does not fit),
     * up to {@code maxLines}, only of messages still showing.
     *
     * @param newestFirst the latest messages, newest first
     */
    public static List<Line> visible(List<Message> newestFirst, int maxLines) {
        List<Line> out = new ArrayList<>();
        for (int m = 0; m < newestFirst.size() && out.size() < maxLines; m++) {
            Message message = newestFirst.get(m);
            double alpha = fade(message.age());
            if (alpha <= 0.0) {
                break; // older ones are older still
            }
            for (int line = message.lines() - 1; line >= 0 && out.size() < maxLines; line--) {
                out.add(new Line(m, line, alpha));
            }
        }
        return out;
    }

    /** The whole panel's opacity: that of its most opaque line (the frame fades with the last message). */
    public static double panelAlpha(List<Line> lines) {
        double best = 0.0;
        for (Line line : lines) {
            best = Math.max(best, line.alpha());
        }
        return best;
    }
}
