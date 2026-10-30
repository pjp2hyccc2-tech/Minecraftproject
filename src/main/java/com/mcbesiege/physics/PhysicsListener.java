package com.mcbesiege.physics;

/** Callbacks from the physics world (all on the simulation thread). */
public interface PhysicsListener {
    /**
     * A new contact against a world box was created. Return false to make it non-solid for the rest of the tick
     * (for example because the impact destroyed the block).
     */
    default boolean onWorldImpact(RigidBody body, Collider c, StaticBox box, V3 point, V3 normal, double approachSpeed, double effectiveMass) {
        return true;
    }

    /** A new contact between two bodies was created with the given approach speed (m/s). */
    default void onBodyImpact(RigidBody a, Collider ca, RigidBody b, Collider cb, V3 point, V3 normal, double approachSpeed, double effectiveMass) {}

    /** A joint exceeded its break force / torque and was removed. */
    default void onJointBroken(Joint j) {}
}
