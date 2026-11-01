package com.frostbound.core.weather;

import com.frostbound.core.Difficulty;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.util.Rng;
import com.frostbound.core.util.Tree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Weather in six-hour slots with a 72-hour forecast that is generated ahead of time, so what the
 * forecast shows is exactly what will happen. Cold waves are scripted per difficulty; the great cold
 * wave arrives at the end of the 100 days.
 */
public final class WeatherSystem {
    public static final int SLOT_STEPS = SimClock.STEPS_PER_HOUR * 6;
    public static final int FORECAST_SLOTS = 12;
    public static final int FINAL_DAY = 100;

    /** A scripted cold wave: first day, number of days, state. */
    public record Wave(int startDay, int days, WeatherState state) {
        public boolean covers(int day) {
            return day >= startDay && day < startDay + days;
        }
    }

    private final Difficulty difficulty;
    private final List<Wave> waves;
    private WeatherState current = WeatherState.CLEAR;
    private final List<WeatherState> forecast = new ArrayList<>();
    private long slot;

    public WeatherSystem(Difficulty difficulty) {
        this.difficulty = difficulty;
        this.waves = wavesFor(difficulty);
    }

    public static List<Wave> wavesFor(Difficulty d) {
        List<Wave> w = new ArrayList<>();
        switch (d) {
            case EASY -> {
                w.add(new Wave(30, 1, WeatherState.EXTREME_COLD));
                w.add(new Wave(60, 2, WeatherState.EXTREME_COLD));
                w.add(new Wave(85, 2, WeatherState.EXTREME_COLD));
            }
            case NORMAL -> {
                w.add(new Wave(24, 2, WeatherState.EXTREME_COLD));
                w.add(new Wave(48, 2, WeatherState.EXTREME_COLD));
                w.add(new Wave(72, 3, WeatherState.EXTREME_COLD));
            }
            case HARD -> {
                w.add(new Wave(18, 2, WeatherState.EXTREME_COLD));
                w.add(new Wave(36, 2, WeatherState.EXTREME_COLD));
                w.add(new Wave(54, 3, WeatherState.EXTREME_COLD));
                w.add(new Wave(72, 3, WeatherState.EXTREME_COLD));
                w.add(new Wave(88, 3, WeatherState.EXTREME_COLD));
            }
        }
        w.add(new Wave(FINAL_DAY - 2, 3, WeatherState.GREAT_COLD_WAVE));
        return w;
    }

    /** Fills the forecast for a new game. */
    public void init(Rng rng) {
        slot = 0;
        current = WeatherState.CLEAR;
        forecast.clear();
        WeatherState prev = current;
        for (int i = 1; i <= FORECAST_SLOTS; i++) {
            prev = generate(slot + i, prev, rng);
            forecast.add(prev);
        }
    }

    /** Called every step; changes the weather at slot boundaries. Returns true when the state changed. */
    public boolean step(long clockStep, Rng rng) {
        long newSlot = clockStep / SLOT_STEPS;
        if (newSlot == slot) {
            return false;
        }
        WeatherState before = current;
        while (slot < newSlot) {
            slot++;
            if (forecast.isEmpty()) {
                current = generate(slot, current, rng);
            } else {
                current = forecast.remove(0);
            }
            WeatherState last = forecast.isEmpty() ? current : forecast.get(forecast.size() - 1);
            while (forecast.size() < FORECAST_SLOTS) {
                last = generate(slot + forecast.size() + 1, last, rng);
                forecast.add(last);
            }
        }
        return before != current;
    }

    private WeatherState generate(long slotIndex, WeatherState prev, Rng rng) {
        int day = (int) (slotIndex * SLOT_STEPS / SimClock.STEPS_PER_DAY) + 1;
        for (Wave w : waves) {
            if (w.covers(day)) {
                return w.state();
            }
        }
        if (prev != WeatherState.EXTREME_COLD && prev != WeatherState.GREAT_COLD_WAVE && rng.chance(0.45)) {
            return prev;
        }
        double[] p;
        if (day <= 30) {
            p = new double[] {45, 35, 15, 5, 0};
        } else if (day <= 70) {
            p = new double[] {30, 35, 20, 15, 0};
        } else if (day <= FINAL_DAY) {
            p = new double[] {20, 30, 25, 25, 0};
        } else {
            p = new double[] {10, 30, 25, 25, 10};
        }
        if (difficulty == Difficulty.HARD) {
            p[0] -= 5;
            p[3] += 5;
        } else if (difficulty == Difficulty.EASY) {
            p[0] += 5;
            p[3] -= Math.min(5, p[3]);
        }
        double total = 0;
        for (double v : p) {
            total += v;
        }
        double r = rng.nextDouble() * total;
        WeatherState[] order = {WeatherState.CLEAR, WeatherState.SNOW, WeatherState.WIND, WeatherState.BLIZZARD, WeatherState.EXTREME_COLD};
        for (int i = 0; i < p.length; i++) {
            r -= p[i];
            if (r < 0) {
                return order[i];
            }
        }
        return WeatherState.SNOW;
    }

    /** Base temperature for a day before weather and time of day (degrees C). */
    public static double baseTemperature(int day) {
        double[][] pts = {{1, -20}, {20, -25}, {60, -40}, {90, -50}, {100, -54}};
        if (day <= 1) {
            return -20;
        }
        for (int i = 1; i < pts.length; i++) {
            if (day <= pts[i][0]) {
                double t = (day - pts[i - 1][0]) / (pts[i][0] - pts[i - 1][0]);
                return pts[i - 1][1] + t * (pts[i][1] - pts[i - 1][1]);
            }
        }
        return Math.max(-64, -54 - 0.1 * (day - 100));
    }

    /** Outside temperature right now. */
    public double outsideTemperature(SimClock clock) {
        double h = clock.hourFraction();
        double diurnal = 4.0 * Math.cos(2 * Math.PI * (h - 15.0) / 24.0);
        return baseTemperature(clock.day()) + diurnal + current.tempOffset + difficulty.tempOffset;
    }

    /** Expected outside temperature at noon of a future day under a given state (for forecasts). */
    public double expectedTemperature(int day, WeatherState state) {
        return baseTemperature(day) + state.tempOffset + difficulty.tempOffset;
    }

    public WeatherState current() {
        return current;
    }

    /** Forces the current weather (debug command); the forecast is kept. */
    public void force(WeatherState state) {
        current = state;
    }

    public List<WeatherState> forecast() {
        return List.copyOf(forecast);
    }

    public List<Wave> waves() {
        return waves;
    }

    /** The next scripted wave starting after {@code day}, or null. */
    public Wave nextWaveAfter(int day) {
        for (Wave w : waves) {
            if (w.startDay() > day) {
                return w;
            }
        }
        return null;
    }

    public Map<String, Object> save() {
        Map<String, Object> m = Tree.map();
        m.put("current", current.name());
        m.put("slot", slot);
        List<Object> f = new ArrayList<>();
        for (WeatherState s : forecast) {
            f.add(s.name());
        }
        m.put("forecast", f);
        return m;
    }

    public void load(Map<String, Object> m, Rng rng) {
        current = Tree.e(m, "current", WeatherState.class, WeatherState.CLEAR);
        slot = Tree.l(m, "slot", 0L, 0L, Long.MAX_VALUE / 4);
        forecast.clear();
        for (String s : Tree.strings(m, "forecast")) {
            try {
                forecast.add(WeatherState.valueOf(s));
            } catch (IllegalArgumentException e) {
                Tree.repaired("forecast=" + s);
            }
        }
        WeatherState last = forecast.isEmpty() ? current : forecast.get(forecast.size() - 1);
        while (forecast.size() < FORECAST_SLOTS) {
            last = generate(slot + forecast.size() + 1, last, rng);
            forecast.add(last);
        }
        while (forecast.size() > FORECAST_SLOTS) {
            forecast.remove(forecast.size() - 1);
        }
    }
}
