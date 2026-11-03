package com.hitori.entity;

/** Animation state shared by all HITORI spirits and the medium (read by the client models). */
public interface Animated {
    /** Pose id (see docs/DESIGN.md §5). */
    int pose();

    /** Ticks since the current pose began (with partial tick). */
    float poseTime(float partialTick);

    /** 0..1 strength of the unnatural stutter: frame skips, jitter, joints bending the wrong way. */
    default float glitch(float partialTick) {
        return 0.0f;
    }
}
