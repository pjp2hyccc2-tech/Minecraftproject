package com.eternalrealm.estate;

import java.util.Objects;
import java.util.Optional;

/**
 * One estate in the registry: its key (kind, owner, current design), the plot its design is (being) built on, and
 * while a redesign or a refresh is under way the {@code previous} plot, which still stands and is used until the new
 * build is complete. Immutable.
 *
 * @param previous the plot in use during a redesign, or {@code null}
 */
public record EstateRecord(EstateKey key, EstatePlot plot, EstatePlot previous, long createdAtMillis) {
    public EstateRecord {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(plot, "plot");
        if (plot.equals(previous)) {
            throw new IllegalArgumentException("previous plot equals the plot: " + plot);
        }
        if (plot.region() != EstatePlots.regionOf(key.kind())
                || previous != null && previous.region() != plot.region()) {
            throw new IllegalArgumentException(plot + " is not in the region of " + key.kind());
        }
    }

    public String id() {
        return key.id();
    }

    public Optional<EstatePlot> previousPlot() {
        return Optional.ofNullable(previous);
    }

    /** Whether a new build is replacing the one on {@link #previous()}. */
    public boolean moving() {
        return previous != null;
    }

    /** The plot players use: the previous one while a redesign is being built, else the plot. */
    public EstatePlot livePlot() {
        return previous != null ? previous : plot;
    }

    EstateRecord withMove(String design, EstatePlot newPlot) {
        return new EstateRecord(key.withDesign(design), newPlot, livePlot(), createdAtMillis);
    }

    EstateRecord moved() {
        return new EstateRecord(key, plot, null, createdAtMillis);
    }
}
