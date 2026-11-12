package com.eternalrealm.job.fx;

/**
 * One playing show: where the caster began, what it aimed at, and where it stood at each hit frame (strikes stay where
 * they were cut while the caster moves on). Fed every frame with the caster's current pose.
 */
public final class ShowState {
    private final SkillShow show;
    private final Pose origin;
    private final V3 aim;
    private final Pose[] frames;
    private Pose now;

    public ShowState(SkillShow show, Pose origin, V3 aim) {
        this.show = show;
        this.origin = origin;
        this.aim = aim;
        this.frames = new Pose[show.frameTimes().length];
        this.now = origin;
    }

    public SkillShow show() {
        return show;
    }

    /** Takes the caster's pose at show time {@code t}; hit frames that have come keep the pose of their moment. */
    public void update(double t, Pose current) {
        if (current == null) {
            return;
        }
        now = current;
        int[] times = show.frameTimes();
        for (int i = 0; i < times.length; i++) {
            if (frames[i] == null && t >= times[i]) {
                frames[i] = current;
            }
        }
    }

    public Pose origin() {
        return origin;
    }

    public V3 aim() {
        return aim;
    }

    public Pose now() {
        return now;
    }

    /** The pose at hit frame {@code index} (the current pose until it has come). */
    public Pose frame(int index) {
        if (index < 0 || index >= frames.length || frames[index] == null) {
            return now;
        }
        return frames[index];
    }
}
