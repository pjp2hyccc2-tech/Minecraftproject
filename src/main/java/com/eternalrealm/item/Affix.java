package com.eternalrealm.item;

import java.util.Objects;

/**
 * One random bonus of an equipment piece.
 *
 * @param kind  what the bonus raises
 * @param value positive, on the kind's grid ({@link AffixKind#isOnGrid}) and at most {@link AffixKind#cap()}; percent
 *              kinds are fractions (0.075 = +7.5 %)
 */
public record Affix(AffixKind kind, double value) {
    public Affix {
        Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(value) || value <= 0.0 || value > kind.cap()) {
            throw new IllegalArgumentException(kind.id() + " value out of range (0, " + kind.cap() + "]: " + value);
        }
        if (!kind.isOnGrid(value)) {
            throw new IllegalArgumentException(kind.id() + " value is not a multiple of " + kind.step() + ": " + value);
        }
        value = kind.snap(value);
    }

    /** The value of a primary stat kind as a whole number. */
    public int intValue() {
        return (int) Math.round(value);
    }
}
