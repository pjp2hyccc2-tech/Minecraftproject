package com.eternalrealm.social.phone;

import java.util.regex.Pattern;

/**
 * What players write on the 魔導スマホ (messages, posts, comments), cleaned before it is kept: no formatting codes
 * ({@code §}), no control or invisible formatting characters (no right-to-left tricks), no broken surrogate halves;
 * newlines only where allowed (at most {@link #MAX_LINES} lines, blank runs collapsed), the ends trimmed. Pure.
 */
public final class PhoneText {
    public static final int MESSAGE_MAX = 200;
    public static final int POST_MAX = 280;
    public static final int COMMENT_MAX = 140;
    public static final int MAX_LINES = 5;
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private PhoneText() {
    }

    /** The text as it will be kept ("" when nothing is left). */
    public static String clean(String raw, boolean multiline) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        boolean newline = false;
        int lines = 1;
        int i = 0;
        while (i < raw.length()) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') { // a formatting code: drop it and its letter
                if (i < raw.length()) {
                    i += Character.charCount(raw.codePointAt(i));
                }
                continue;
            }
            if (cp == '\n' || cp == '\r' || cp == 0x2028 || cp == 0x2029) {
                newline = out.length() > 0;
                continue;
            }
            if (cp == '\t') {
                cp = ' ';
            }
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT
                    || Character.getType(cp) == Character.SURROGATE || Character.getType(cp) == Character.UNASSIGNED) {
                continue;
            }
            if (newline) {
                newline = false;
                if (multiline && lines < MAX_LINES) {
                    while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
                        out.setLength(out.length() - 1);
                    }
                    out.append('\n');
                    lines++;
                } else {
                    out.append(' ');
                }
            }
            out.appendCodePoint(cp);
        }
        return out.toString().strip();
    }

    /** At most {@code max} characters (code points). */
    public static boolean fits(String text, int max) {
        return text.codePointCount(0, text.length()) <= max;
    }

    /** A Minecraft player name ({@code [A-Za-z0-9_]{1,16}}). */
    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** The first {@code max} characters, with "…" when cut (for previews). */
    public static String preview(String text, int max) {
        if (fits(text, max)) {
            return text.replace('\n', ' ');
        }
        int end = text.offsetByCodePoints(0, Math.max(0, max - 1));
        return text.substring(0, end).replace('\n', ' ') + "…";
    }
}
