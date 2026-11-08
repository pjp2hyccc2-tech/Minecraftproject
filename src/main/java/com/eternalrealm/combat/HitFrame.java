package com.eternalrealm.combat;

import java.util.Objects;

/**
 * One damage instant of an action.
 *
 * @param offsetTicks ticks after the action starts (before attack-speed scaling)
 * @param multiplier  damage multiplier of this frame
 * @param knockback   knockback strength applied to targets (vanilla default for a hit is 0.4)
 */
public record HitFrame(int offsetTicks, HitShape shape, double multiplier, double knockback) {
    public HitFrame {
        Check.range("offsetTicks", offsetTicks, 0, 200);
        Objects.requireNonNull(shape, "shape");
        Check.range("multiplier", multiplier, 0.01, 20.0);
        Check.range("knockback", knockback, 0.0, 5.0);
    }
}
