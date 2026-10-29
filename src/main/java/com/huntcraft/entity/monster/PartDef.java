package com.huntcraft.entity.monster;

/**
 * Definition of a monster body part (hitbox + hitzone + break rules).
 * Offsets are in blocks at monster scale 1.0, relative to the monster's feet position:
 * side (+ = monster's right), up, forward (+ = facing direction).
 */
public final class PartDef {
    public final String name;
    public final float side, up, forward;
    public final float width, height;
    public final Hitzone hitzone;
    /** Damage needed to break (0 = unbreakable). Scaled by the rank health multiplier. */
    public float breakThreshold = 0;
    /** Tail that can be severed with CUT damage. */
    public boolean severable = false;
    /** Model bones hidden once broken (e.g. horn tips, tail segments). */
    public String[] hideBones = new String[0];
    /** Blunt damage on this part builds KO (stun). */
    public boolean koZone = false;
    /** Hitzone bonus once broken. */
    public int brokenSoften = 10;
    /** If false this part only exists as a hitzone reference (no separate hitbox entity), e.g. "body". */
    public boolean hasHitbox = true;

    public PartDef(String name, float side, float up, float forward, float width, float height, Hitzone hitzone) {
        this.name = name;
        this.side = side;
        this.up = up;
        this.forward = forward;
        this.width = width;
        this.height = height;
        this.hitzone = hitzone;
    }

    public static PartDef body(Hitzone hz) {
        PartDef p = new PartDef("body", 0, 0, 0, 0, 0, hz);
        p.hasHitbox = false;
        return p;
    }

    public PartDef breakable(float threshold, String... hideBones) {
        this.breakThreshold = threshold;
        this.hideBones = hideBones;
        return this;
    }

    public PartDef severable(float threshold, String... hideBones) {
        this.breakThreshold = threshold;
        this.severable = true;
        this.hideBones = hideBones;
        return this;
    }

    public PartDef ko() {
        this.koZone = true;
        return this;
    }

    public boolean isBreakable() {
        return breakThreshold > 0;
    }
}
