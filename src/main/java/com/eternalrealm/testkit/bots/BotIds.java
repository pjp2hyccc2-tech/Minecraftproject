package com.eternalrealm.testkit.bots;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Who the test bots are (docs/modules/testkit.md §4), the same on the server and on every client: bot {@code n}
 * ({@code 1..}{@value #MAX}) always has the same id, name and colour. The server logs them in under these ids (so a
 * bot's character carries on from one test session to the next); a client recognises them by the id and draws each in
 * its own single colour.
 */
public final class BotIds {
    /** At most this many bots at once. */
    public static final int MAX = 64;

    private static final double[] SATURATIONS = {0.5, 0.7, 0.9};
    private static final double[] VALUES = {0.72, 0.95};
    private static final UUID[] IDS = new UUID[MAX + 1];
    private static final Map<UUID, Integer> NUMBERS = new HashMap<>();

    static {
        for (int number = 1; number <= MAX; number++) {
            UUID id = UUID.nameUUIDFromBytes(("eternalrealm:testbot/" + number).getBytes(StandardCharsets.UTF_8));
            IDS[number] = id;
            NUMBERS.put(id, number);
        }
    }

    private BotIds() {
    }

    /** The fixed id of bot {@code number}. */
    public static UUID idOf(int number) {
        check(number);
        return IDS[number];
    }

    /** The name of bot {@code number}: Bot01, Bot02... (a valid player name: letters and digits, at most 16). */
    public static String nameOf(int number) {
        check(number);
        return String.format("Bot%02d", number);
    }

    /** The number of the bot with this id; empty for anyone else. */
    public static OptionalInt numberOf(UUID id) {
        Integer number = id == null ? null : NUMBERS.get(id);
        return number == null ? OptionalInt.empty() : OptionalInt.of(number);
    }

    public static boolean isBot(UUID id) {
        return id != null && NUMBERS.containsKey(id);
    }

    /**
     * The bot's colour (0xRRGGBB): hues a golden angle apart, so neighbours in the list never look alike, at three
     * saturations and two brightnesses so that bots far apart in the list do not either.
     */
    public static int colorOf(int number) {
        check(number);
        double hue = (number * 0.6180339887498949) % 1.0;
        double saturation = SATURATIONS[number % SATURATIONS.length];
        double value = VALUES[number % VALUES.length];
        return hsv(hue, saturation, value);
    }

    /** HSV (each 0..1) to 0xRRGGBB. */
    static int hsv(double hue, double saturation, double value) {
        double h = (hue - Math.floor(hue)) * 6.0;
        int sector = (int) Math.floor(h) % 6;
        double f = h - Math.floor(h);
        double p = value * (1.0 - saturation);
        double q = value * (1.0 - saturation * f);
        double t = value * (1.0 - saturation * (1.0 - f));
        double r;
        double g;
        double b;
        switch (sector) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            default -> { r = value; g = p; b = q; }
        }
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    private static int channel(double unit) {
        return Math.max(0, Math.min(255, (int) Math.round(unit * 255.0)));
    }

    private static void check(int number) {
        if (number < 1 || number > MAX) {
            throw new IllegalArgumentException("bot number out of range [1, " + MAX + "]: " + number);
        }
    }
}
