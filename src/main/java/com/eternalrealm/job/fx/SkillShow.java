package com.eternalrealm.job.fx;

import com.eternalrealm.job.JobType;
import com.eternalrealm.job.SkillTheme;

import java.util.List;

/**
 * The light of one job skill from its first tick to the last fading glow: its layers ({@link Cue}s) on the skill's own
 * timeline ({@code duration} ticks of the action, then {@code tail} ticks of afterglow) and the moments that shake
 * the view. A finale (秘奥義) also directs the screen ({@link Screen}: flashes, cinema bars, a zoom, a vignette) and hides
 * the caster's weapon or body for stretches ({@link Window}) while a prop or an after-image stands in for it.
 *
 * @param frameTimes the hit frames' offsets (anchors of {@link Cue.Anchor#FRAME} layers)
 */
public record SkillShow(String skillId, JobType job, SkillTheme theme, boolean ultimate, int duration, int tail,
                        int[] frameTimes, List<Cue> cues, List<Shake> shakes, List<Screen> screens,
                        List<Window> hidden) {
    /** A shake of the view at {@code time}: {@code intensity} for {@code ticks}, and a kick of {@code kick} degrees. */
    public record Shake(double time, float intensity, int ticks, float kick) {
    }

    /**
     * A moment on the screen at {@code time} for {@code ticks}: a {@link Kind#FLASH} of {@code rgb} as strong as
     * {@code strength} (0..1) fading out, {@link Kind#BARS} sliding in to {@code strength} of the screen height and
     * out again, a {@link Kind#ZOOM} of the field of view by the factor {@code strength} (below 1 closes in) easing in
     * and out, or a {@link Kind#VIGNETTE} of {@code rgb} darkening the edges.
     */
    public record Screen(double time, Kind kind, int ticks, float strength, int rgb) {
        public enum Kind {
            FLASH,
            BARS,
            ZOOM,
            VIGNETTE
        }

        public Screen {
            if (ticks < 1 || !Float.isFinite(strength)) {
                throw new IllegalArgumentException("screen " + kind + " for " + ticks + " ticks");
            }
        }

        /** How strong it is {@code since} ticks after its time: 0 outside, an ease in and out for bars and zooms. */
        public double weight(double since) {
            if (since < 0.0 || since > ticks) {
                return 0.0;
            }
            return switch (kind) {
                case FLASH -> Math.pow(1.0 - since / ticks, 1.6);
                case BARS, ZOOM, VIGNETTE -> {
                    double edge = Math.min(8.0, ticks * 0.3);
                    double in = Math.min(1.0, since / edge);
                    double out = Math.min(1.0, (ticks - since) / edge);
                    double k = Math.min(in, out);
                    yield k * k * (3.0 - 2.0 * k);
                }
            };
        }
    }

    /** From {@code start} to {@code end}: the caster's held weapon is not drawn (and with {@code body} the caster). */
    public record Window(double start, double end, boolean body) {
        public boolean covers(double t) {
            return t >= start && t <= end;
        }
    }

    public SkillShow {
        cues = List.copyOf(cues);
        shakes = List.copyOf(shakes);
        screens = List.copyOf(screens);
        hidden = List.copyOf(hidden);
        frameTimes = frameTimes.clone();
    }

    /** A show without screen moments or hidden stretches (every skill but the finales). */
    public SkillShow(String skillId, JobType job, SkillTheme theme, boolean ultimate, int duration, int tail,
                     int[] frameTimes, List<Cue> cues, List<Shake> shakes) {
        this(skillId, job, theme, ultimate, duration, tail, frameTimes, cues, shakes, List.of(), List.of());
    }

    /** Ticks from the start until the last light is gone. */
    public int length() {
        return duration + tail;
    }

    /**
     * What the screen shows at show time {@code t}: every flash blended together (as the light adds up), the
     * strongest bars and vignette, and every zoom multiplied.
     *
     * @param flash         how much of the screen the flash covers (0..1)
     * @param flashRgb      its colour (the flashes' colours weighted by their strength)
     * @param bars          the cinema bars' share of the screen height (each bar)
     * @param zoom          the factor on the field of view (1: none, below 1: closer)
     * @param vignette      how dark the edges are (0..1)
     * @param vignetteRgb   their colour
     */
    public record ScreenState(float flash, int flashRgb, float bars, float zoom, float vignette, int vignetteRgb) {
        public static final ScreenState NONE = new ScreenState(0.0F, 0, 0.0F, 1.0F, 0.0F, 0);

        /** Whether nothing shows. */
        public boolean none() {
            return flash <= 0.001F && bars <= 0.001F && Math.abs(zoom - 1.0F) <= 0.001F && vignette <= 0.001F;
        }
    }

    /** The screen at show time {@code t} ({@link ScreenState#NONE} for a show without screen moments). */
    public ScreenState screenAt(double t) {
        if (screens.isEmpty()) {
            return ScreenState.NONE;
        }
        double clear = 1.0;
        double r = 0.0;
        double g = 0.0;
        double b = 0.0;
        double weightSum = 0.0;
        double bars = 0.0;
        double zoom = 1.0;
        double vignette = 0.0;
        int vignetteRgb = 0;
        for (Screen s : screens) {
            double w = s.weight(t - s.time());
            if (w <= 0.0) {
                continue;
            }
            double k = Math.max(0.0, Math.min(1.0, s.strength())) * w;
            switch (s.kind()) {
                case FLASH -> {
                    clear *= 1.0 - k;
                    r += ((s.rgb() >> 16) & 0xFF) * k;
                    g += ((s.rgb() >> 8) & 0xFF) * k;
                    b += (s.rgb() & 0xFF) * k;
                    weightSum += k;
                }
                case BARS -> bars = Math.max(bars, Math.min(0.25, s.strength()) * w);
                case ZOOM -> zoom *= 1.0 + (Math.max(0.3, Math.min(2.0, s.strength())) - 1.0) * w;
                case VIGNETTE -> {
                    if (k > vignette) {
                        vignette = k;
                        vignetteRgb = s.rgb();
                    }
                }
            }
        }
        int flashRgb = weightSum <= 0.0 ? 0 : ((int) Math.round(r / weightSum) << 16)
                | ((int) Math.round(g / weightSum) << 8) | (int) Math.round(b / weightSum);
        return new ScreenState((float) (1.0 - clear), flashRgb & 0xFFFFFF, (float) bars, (float) zoom,
                (float) vignette, vignetteRgb & 0xFFFFFF);
    }

    /** The last moment anything shows on the screen (0 without screen moments). */
    public double screenEnd() {
        double end = 0.0;
        for (Screen s : screens) {
            end = Math.max(end, s.time() + s.ticks());
        }
        return end;
    }

    /** Whether the caster's weapon is hidden at show time {@code t}. */
    public boolean hidesWeapon(double t) {
        for (Window w : hidden) {
            if (w.covers(t)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the caster itself is hidden at show time {@code t} (a flash step: only its after-images show). */
    public boolean hidesBody(double t) {
        for (Window w : hidden) {
            if (w.body() && w.covers(t)) {
                return true;
            }
        }
        return false;
    }
}
