package com.eternalrealm.item;

/**
 * Where a weapon art's choreography ({@link ArtChoreography}) is at a moment of the action: winding up, chambering
 * the next strike, striking (which hit, how far), holding the finish, or returning. Every strike takes the last few
 * ticks before its hit (fewer when hits follow fast) and ends just after it, so the blade meets the target on the tick
 * the hit lands. Pure; times are ticks since the action began.
 */
public final class ArtTimeline {
    /** The longest strike (ticks up to and including the hit). */
    public static final int MAX_LEAD = 3;

    public enum Phase {
        /** Moving into the wind-up stance and holding it. */
        WINDUP,
        /** Between two strikes: from the end of the last into the start of the next ({@code hit} = the next). */
        CHAMBER,
        /** The strike of {@code hit}. */
        STRIKE,
        /** From the last strike into the finishing stance. */
        FINISH,
        /** Back to the ready pose. */
        RECOVER
    }

    /** @param progress 0..1 through the phase */
    public record Moment(Phase phase, int hit, float progress) {
    }

    private ArtTimeline() {
    }

    /** Ticks the strike of hit {@code k} starts before the hit. */
    public static int lead(int[] hits, int k) {
        int gap = k == 0 ? hits[0] : hits[k] - hits[k - 1];
        return Math.max(1, Math.min(MAX_LEAD, gap - 1));
    }

    public static float strikeStart(int[] hits, int k) {
        return hits[k] - lead(hits, k);
    }

    public static float strikeEnd(int[] hits, int k) {
        return hits[k] + 1.0F;
    }

    /** When the finishing stance is reached (the rest of the action is the return). */
    public static float finishEnd(int[] hits, int duration) {
        float end = strikeEnd(hits, hits.length - 1);
        return end + Math.max(2.0F, (duration - end) * 0.55F);
    }

    /**
     * @param hits     the ticks of the hits or launches, ascending (at least one)
     * @param duration the action's length in ticks
     * @param elapsed  ticks since the action began (may be fractional)
     */
    public static Moment at(int[] hits, int duration, float elapsed) {
        if (hits.length == 0) {
            throw new IllegalArgumentException("an art without hits has no choreography");
        }
        float first = strikeStart(hits, 0);
        if (elapsed < first) {
            return new Moment(Phase.WINDUP, 0, clamp(elapsed / Math.max(1.0F, first)));
        }
        for (int k = 0; k < hits.length; k++) {
            float start = strikeStart(hits, k);
            float end = strikeEnd(hits, k);
            if (k > 0) {
                float previousEnd = strikeEnd(hits, k - 1);
                if (elapsed >= previousEnd && elapsed < start) {
                    return new Moment(Phase.CHAMBER, k, clamp((elapsed - previousEnd) / Math.max(0.5F,
                            start - previousEnd)));
                }
            }
            if (elapsed >= start && elapsed < end) {
                return new Moment(Phase.STRIKE, k, clamp((elapsed - start) / Math.max(0.5F, end - start)));
            }
        }
        int last = hits.length - 1;
        float end = strikeEnd(hits, last);
        float finish = finishEnd(hits, duration);
        if (elapsed < finish) {
            return new Moment(Phase.FINISH, last, clamp((elapsed - end) / Math.max(0.5F, finish - end)));
        }
        return new Moment(Phase.RECOVER, last, clamp((elapsed - finish) / Math.max(1.0F, duration - finish)));
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
