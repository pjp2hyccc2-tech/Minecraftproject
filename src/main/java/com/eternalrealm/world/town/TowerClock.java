package com.eternalrealm.world.town;

/**
 * Time on the capital's clock faces. Minecraft's day starts at 06:00 (day time 0); one in-game hour is 1000 ticks.
 * Angles are clockwise from twelve o'clock, in degrees.
 */
public final class TowerClock {
    public static final int TICKS_PER_HOUR = 1000;
    public static final int TICKS_PER_DAY = 24000;

    private TowerClock() {
    }

    /** Hour of the day 0..23 for a day time (any long, negative or past one day). */
    public static int hour(long dayTime) {
        long t = Math.floorMod(dayTime, (long) TICKS_PER_DAY);
        return (int) ((t / TICKS_PER_HOUR + 6) % 24);
    }

    /** Minutes past the hour, fractional (0 <= m < 60). */
    public static double minutes(double dayTime) {
        double t = dayTime - Math.floor(dayTime / TICKS_PER_DAY) * TICKS_PER_DAY;
        return (t % TICKS_PER_HOUR) * 60.0 / TICKS_PER_HOUR;
    }

    /** The minute hand: one turn per in-game hour. */
    public static double minuteAngle(double dayTime) {
        return minutes(dayTime) * 6.0;
    }

    /** The hour hand: one turn per twelve hours, moving smoothly between the hours. */
    public static double hourAngle(double dayTime) {
        double t = dayTime - Math.floor(dayTime / TICKS_PER_DAY) * TICKS_PER_DAY;
        double hours = (t / TICKS_PER_HOUR + 6.0) % 12.0;
        return hours * 30.0;
    }

    /** How many times the bell strikes at the top of the given hour (0..23): 12 at noon and midnight. */
    public static int strikes(int hour) {
        int h = Math.floorMod(hour, 12);
        return h == 0 ? 12 : h;
    }
}
