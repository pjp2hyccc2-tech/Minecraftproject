package com.eternalrealm.combat;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Objects;

/**
 * Timing and effects of one action (a combo step, a heavy strike, a skill or the dodge).
 *
 * <p>All tick values are relative to the action start. The action can be followed by the next action from
 * {@code cancelTicks} on ("cancel point"); it is fully over at {@code durationTicks}. Every hit frame happens before
 * the cancel point, so starting the next action never skips a hit of the previous one.</p>
 *
 * @param speedScaled       whether attack speed (AGI) scales this action's timing (light and heavy attacks only)
 * @param staminaCost       stamina spent when the action starts
 * @param cooldownTicks     skill cooldown started when the action starts (skills only)
 * @param invulnerableTicks ticks of damage immunity from the start (dodge only)
 */
public record ActionDefinition(String id, ActionKind kind, int durationTicks, int cancelTicks, List<HitFrame> frames,
                               double staminaCost, int cooldownTicks, int invulnerableTicks, boolean speedScaled) {
    public static final int MAX_DURATION = 200;

    public ActionDefinition {
        Check.id("id", id);
        Objects.requireNonNull(kind, "kind");
        Check.range("durationTicks", durationTicks, 1, MAX_DURATION);
        Check.range("cancelTicks", cancelTicks, 1, durationTicks);
        frames = List.copyOf(frames);
        int previous = -1;
        for (HitFrame frame : frames) {
            if (frame.offsetTicks() < previous) {
                throw new IllegalArgumentException(id + ": hit frames must be ordered by offset");
            }
            if (frame.offsetTicks() >= cancelTicks) {
                throw new IllegalArgumentException(id + ": hit frame at " + frame.offsetTicks()
                        + " is not before the cancel point " + cancelTicks);
            }
            previous = frame.offsetTicks();
        }
        if (kind == ActionKind.DODGE ? !frames.isEmpty() : frames.isEmpty()) {
            throw new IllegalArgumentException(id + ": dodges have no hit frames, every other action has at least one");
        }
        Check.range("staminaCost", staminaCost, 0.0, 1000.0);
        Check.range("cooldownTicks", cooldownTicks, 0, 20 * 600);
        Check.range("invulnerableTicks", invulnerableTicks, 0, durationTicks);
        if (cooldownTicks > 0 && kind != ActionKind.SKILL) {
            throw new IllegalArgumentException(id + ": only skills have a cooldown");
        }
        if (invulnerableTicks > 0 && kind != ActionKind.DODGE) {
            throw new IllegalArgumentException(id + ": only dodges grant invulnerability");
        }
    }

    public String translationKey() {
        return "action." + ModInfo.MOD_ID + "." + id;
    }

    /**
     * Absolute timing of one execution.
     *
     * @param speedFactor duration multiplier from attack speed, in {@code (0, 1]}; ignored unless {@link #speedScaled}
     */
    public Timing timing(double speedFactor) {
        double factor = speedScaled ? Check.range("speedFactor", speedFactor, 0.05, 1.0) : 1.0;
        int[] offsets = new int[frames.size()];
        int lastFrame = -1;
        for (int i = 0; i < offsets.length; i++) {
            offsets[i] = scale(frames.get(i).offsetTicks(), factor);
            lastFrame = offsets[i];
        }
        int duration = Math.max(scale(durationTicks, factor), lastFrame + 1);
        int cancel = Math.min(duration, Math.max(scale(cancelTicks, factor), lastFrame + 1));
        return new Timing(duration, cancel, offsets);
    }

    private static int scale(int ticks, double factor) {
        if (ticks == 0) {
            return 0;
        }
        return Math.max(1, (int) Math.round(ticks * factor));
    }

    /** Scaled timing; offsets are non-decreasing and all strictly before {@code cancel}; {@code cancel <= duration}. */
    public record Timing(int duration, int cancel, int[] frameOffsets) {
        public Timing {
            frameOffsets = frameOffsets.clone();
        }

        @Override
        public int[] frameOffsets() {
            return frameOffsets.clone();
        }

        int frameOffset(int index) {
            return frameOffsets[index];
        }

        int frameCount() {
            return frameOffsets.length;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Timing other && duration == other.duration && cancel == other.cancel
                    && java.util.Arrays.equals(frameOffsets, other.frameOffsets);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * duration + cancel) + java.util.Arrays.hashCode(frameOffsets);
        }

        @Override
        public String toString() {
            return "Timing{duration=" + duration + ", cancel=" + cancel + ", frames="
                    + java.util.Arrays.toString(frameOffsets) + "}";
        }
    }
}
