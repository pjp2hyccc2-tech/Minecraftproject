package com.eternalrealm.estate;

import java.util.List;

/**
 * What the estate registry persists ({@code estates.json}): every estate and the plots still waiting to be wiped
 * (released, or left behind by a redesign). Immutable.
 */
public record EstateData(List<EstateRecord> estates, List<EstatePlot> wiping) {
    public EstateData {
        estates = List.copyOf(estates);
        wiping = List.copyOf(wiping);
    }

    public static EstateData empty() {
        return new EstateData(List.of(), List.of());
    }
}
