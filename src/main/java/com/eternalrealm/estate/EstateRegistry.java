package com.eternalrealm.estate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who has which estate, on which plot (docs/modules/estates.md): at most one estate per kind and owner; the plots
 * of every estate (and of a redesign's previous build) are taken in the {@link EstatePlots}, and so are released or
 * replaced plots until their blocks are wiped ({@link #wiped}). Every change bumps {@link #revision()} so the service
 * knows when to save. Not thread-safe (server thread).
 *
 * <p>The registry only knows names and plots. Whether a plot's blocks stand is the world's business (the platform
 * keeps that next to the blocks), so is everything stored inside.</p>
 */
public final class EstateRegistry {
    private final Map<String, EstateRecord> records = new LinkedHashMap<>();
    private final Map<EstatePlot, String> byPlot = new HashMap<>();
    private final Set<EstatePlot> wiping = new LinkedHashSet<>();
    private final EstatePlots plots = new EstatePlots();
    private long revision;

    /** The outcome of a claim or a redesign: the result and, if OK, the record now stored. */
    public record Change(EstateResult result, Optional<EstateRecord> record) {
        static Change of(EstateResult result) {
            return new Change(result, Optional.empty());
        }

        static Change ok(EstateRecord record) {
            return new Change(EstateResult.OK, Optional.of(record));
        }
    }

    public EstateRegistry() {
    }

    /**
     * A registry holding {@code data}.
     *
     * @throws IllegalArgumentException if the data is inconsistent (two estates of one kind and owner, a plot used
     *                                  twice); a file holding such data is corrupt
     */
    public static EstateRegistry of(EstateData data) {
        EstateRegistry registry = new EstateRegistry();
        for (EstateRecord record : data.estates()) {
            if (registry.records.putIfAbsent(record.id(), record) != null) {
                throw new IllegalArgumentException("two estates " + record.id());
            }
            registry.index(record.plot(), record.id());
            record.previousPlot().ifPresent(previous -> registry.index(previous, record.id()));
        }
        for (EstatePlot plot : data.wiping()) {
            if (registry.byPlot.containsKey(plot) || !registry.wiping.add(plot) || !registry.plots.take(plot)) {
                throw new IllegalArgumentException(plot + " is both in use and waiting to be wiped");
            }
        }
        return registry;
    }

    private void index(EstatePlot plot, String id) {
        if (byPlot.putIfAbsent(plot, id) != null || !plots.take(plot)) {
            throw new IllegalArgumentException(plot + " is used twice");
        }
    }

    /** What to persist. */
    public EstateData snapshot() {
        return new EstateData(new ArrayList<>(records.values()), new ArrayList<>(wiping));
    }

    public long revision() {
        return revision;
    }

    // ------------------------------------------------------------------ queries

    public Optional<EstateRecord> get(String kind, UUID owner) {
        return get(EstateKey.id(kind, owner));
    }

    public Optional<EstateRecord> get(String id) {
        return Optional.ofNullable(records.get(id));
    }

    /** The estate using {@code plot} (as its plot or as the previous plot of a redesign). */
    public Optional<EstateRecord> atPlot(EstatePlot plot) {
        String id = byPlot.get(plot);
        return id == null ? Optional.empty() : get(id);
    }

    public Collection<EstateRecord> all() {
        return List.copyOf(records.values());
    }

    /** Estates of {@code kind}. */
    public List<EstateRecord> ofKind(String kind) {
        List<EstateRecord> found = new ArrayList<>();
        for (EstateRecord record : records.values()) {
            if (record.key().kind().equals(kind)) {
                found.add(record);
            }
        }
        return found;
    }

    /** Plots whose blocks still have to be wiped before they are free again. */
    public Set<EstatePlot> wiping() {
        return Set.copyOf(wiping);
    }

    public boolean isTaken(EstatePlot plot) {
        return plots.isTaken(plot);
    }

    // ------------------------------------------------------------------ changes

    /** A new estate {@code key} on a fresh plot (whose build the caller starts). */
    public Change claim(EstateKey key, long nowMillis) {
        Objects.requireNonNull(key, "key");
        if (records.containsKey(key.id())) {
            return Change.of(EstateResult.ALREADY_CLAIMED);
        }
        Optional<EstatePlot> plot = plots.allocate(key.kind());
        if (plot.isEmpty()) {
            return Change.of(EstateResult.NO_PLOT);
        }
        EstateRecord record = new EstateRecord(key, plot.get(), null, nowMillis);
        records.put(record.id(), record);
        byPlot.put(plot.get(), record.id());
        revision++;
        return Change.ok(record);
    }

    /**
     * Starts rebuilding estate {@code id} as {@code design} on a fresh plot; its current plot stays in use (as the
     * previous plot) until {@link #finishMove}. The same design again is a refresh (a newer version of the blueprint).
     */
    public Change startMove(String id, String design) {
        EstateRecord record = records.get(id);
        if (record == null) {
            return Change.of(EstateResult.NOT_CLAIMED);
        }
        if (record.moving()) {
            return Change.of(EstateResult.BUSY);
        }
        Optional<EstatePlot> plot = plots.allocate(record.key().kind());
        if (plot.isEmpty()) {
            return Change.of(EstateResult.NO_PLOT);
        }
        EstateRecord moving = record.withMove(design, plot.get());
        records.put(id, moving);
        byPlot.put(plot.get(), id);
        revision++;
        return Change.ok(moving);
    }

    /**
     * The new build of a move stands and the contents went over: the previous plot is let go (it waits to be wiped).
     *
     * @return the previous plot, empty if the estate was not moving
     */
    public Optional<EstatePlot> finishMove(String id) {
        EstateRecord record = records.get(id);
        if (record == null || !record.moving()) {
            return Optional.empty();
        }
        EstatePlot previous = record.previous();
        records.put(id, record.moved());
        byPlot.remove(previous);
        wiping.add(previous);
        revision++;
        return Optional.of(previous);
    }

    /**
     * Gives up a move that has not finished (its new plot waits to be wiped; the previous plot is the estate's plot
     * again, with its old design).
     */
    public Optional<EstatePlot> abandonMove(String id, String oldDesign) {
        EstateRecord record = records.get(id);
        if (record == null || !record.moving()) {
            return Optional.empty();
        }
        EstatePlot abandoned = record.plot();
        records.put(id, new EstateRecord(record.key().withDesign(oldDesign), record.previous(), null,
                record.createdAtMillis()));
        byPlot.remove(abandoned);
        wiping.add(abandoned);
        revision++;
        return Optional.of(abandoned);
    }

    /** Removes estate {@code id}; its plots wait to be wiped. @return the removed record */
    public Optional<EstateRecord> release(String id) {
        EstateRecord record = records.remove(id);
        if (record == null) {
            return Optional.empty();
        }
        byPlot.remove(record.plot());
        wiping.add(record.plot());
        record.previousPlot().ifPresent(previous -> {
            byPlot.remove(previous);
            wiping.add(previous);
        });
        revision++;
        return Optional.of(record);
    }

    /**
     * A plot that stands in the world without belonging to any estate (found when the world and the registry are
     * compared): it is taken and waits to be wiped. @return false if an estate uses it or it is already waiting
     */
    public boolean markOrphan(EstatePlot plot) {
        if (byPlot.containsKey(plot) || wiping.contains(plot)) {
            return false;
        }
        plots.take(plot);
        wiping.add(plot);
        revision++;
        return true;
    }

    /** The blocks of {@code plot} are gone: it is free again. @return false if it was not waiting to be wiped */
    public boolean wiped(EstatePlot plot) {
        if (!wiping.remove(plot)) {
            return false;
        }
        plots.free(plot);
        revision++;
        return true;
    }
}
