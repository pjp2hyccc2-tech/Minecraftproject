package com.eternalrealm.job.fx;

import com.eternalrealm.job.JobType;
import com.eternalrealm.job.SkillTheme;

import java.util.List;

/**
 * The light of one job skill from its first tick to the last fading glow: its layers ({@link Cue}s) on the skill's own
 * timeline ({@code duration} ticks of the action, then {@code tail} ticks of afterglow) and the moments that shake
 * the view.
 *
 * @param frameTimes the hit frames' offsets (anchors of {@link Cue.Anchor#FRAME} layers)
 */
public record SkillShow(String skillId, JobType job, SkillTheme theme, boolean ultimate, int duration, int tail,
                        int[] frameTimes, List<Cue> cues, List<Shake> shakes) {
    /** A shake of the view at {@code time}: {@code intensity} for {@code ticks}, and a kick of {@code kick} degrees. */
    public record Shake(double time, float intensity, int ticks, float kick) {
    }

    public SkillShow {
        cues = List.copyOf(cues);
        shakes = List.copyOf(shakes);
        frameTimes = frameTimes.clone();
    }

    /** Ticks from the start until the last light is gone. */
    public int length() {
        return duration + tail;
    }
}
