package com.frostbound.core.sim;

import com.frostbound.core.util.Tree;
import java.util.Map;

/**
 * Fixed-step game clock.
 *
 * <p>A day is always {@value #STEPS_PER_DAY} steps and every rule is written per step, so the outcome of a
 * day does not depend on the real day length or on the 1x/2x/3x speed. Each server tick adds the speed to
 * an integer accumulator and runs one step whenever it reaches the day length in minutes, which gives
 * {@code speed / dayLengthMinutes} steps per tick without rounding drift. Step 0 is 06:00 on day 1.
 */
public final class SimClock {
    public static final int STEPS_PER_DAY = 1200;
    public static final int STEPS_PER_HOUR = 50;
    public static final int HOURS_PER_DAY = 24;
    /** Hour of day at step 0 of each day. */
    public static final int DAY_START_HOUR = 6;
    private static final int MAX_STEPS_PER_TICK = 16;

    private long step;
    private int speed = 1;
    private boolean paused;
    private int accumulator;

    /** Number of steps to run for one server tick. */
    public int stepsForServerTick(int dayLengthMinutes, boolean externallyPaused) {
        if (paused || externallyPaused) {
            return 0;
        }
        int length = Math.max(1, dayLengthMinutes);
        accumulator += speed;
        int n = 0;
        while (accumulator >= length && n < MAX_STEPS_PER_TICK) {
            accumulator -= length;
            n++;
        }
        if (accumulator >= length) {
            accumulator %= length;
        }
        return n;
    }

    public void advanceOne() {
        step++;
    }

    public long step() {
        return step;
    }

    public void setStep(long step) {
        this.step = Math.max(0, step);
    }

    public int speed() {
        return speed;
    }

    public void setSpeed(int speed) {
        this.speed = Math.max(1, Math.min(3, speed));
    }

    public boolean paused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /** Day number, starting at 1. */
    public int day() {
        return (int) (step / STEPS_PER_DAY) + 1;
    }

    public int stepOfDay() {
        return (int) (step % STEPS_PER_DAY);
    }

    public int hourOfDay() {
        return (DAY_START_HOUR + stepOfDay() / STEPS_PER_HOUR) % HOURS_PER_DAY;
    }

    public int minuteOfHour() {
        return (stepOfDay() % STEPS_PER_HOUR) * 60 / STEPS_PER_HOUR;
    }

    /** Clock hour with fraction, 0.0 to 24.0. */
    public double hourFraction() {
        return (DAY_START_HOUR + stepOfDay() / (double) STEPS_PER_HOUR) % HOURS_PER_DAY;
    }

    public boolean isHourBoundary() {
        return step % STEPS_PER_HOUR == 0;
    }

    public boolean isDayBoundary() {
        return step % STEPS_PER_DAY == 0;
    }

    /** Minecraft day time for this step (0 = 06:00, 24000 per day). */
    public long minecraftDayTime() {
        return step * 20L;
    }

    public static long stepsForHours(double hours) {
        return Math.round(hours * STEPS_PER_HOUR);
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("step", step);
        m.put("speed", speed);
        m.put("paused", paused);
        m.put("acc", accumulator);
        return m;
    }

    public void load(Map<String, Object> m) {
        step = Tree.l(m, "step", 0L, 0L, Long.MAX_VALUE / 4);
        speed = Tree.i(m, "speed", 1, 1, 3);
        paused = Tree.b(m, "paused", false);
        accumulator = Tree.i(m, "acc", 0, 0, 10_000);
    }
}
