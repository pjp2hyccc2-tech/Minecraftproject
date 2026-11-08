package com.eternalrealm.world;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * The areas one player has personally visited this season (exploration ranking, map UI). Immutable; sorted.
 *
 * <p>Independent of the server-wide unlocks: an area can be open for everyone but still undiscovered by a player. The
 * Forge layer adds an area when {@link AreaTracker} reports the player entering it (normally only unlocked areas, since
 * locked ones cannot be entered).</p>
 */
public record Discovery(Set<Integer> areaIds) {
    public static final Discovery NONE = new Discovery(Set.of());

    public Discovery {
        Objects.requireNonNull(areaIds, "areaIds");
        TreeSet<Integer> sorted = new TreeSet<>();
        for (Integer id : areaIds) {
            if (id == null || !Areas.exists(id)) {
                throw new IllegalArgumentException("Unknown area id: " + id);
            }
            sorted.add(id);
        }
        areaIds = Collections.unmodifiableSet(sorted);
    }

    public static Discovery of(Collection<Integer> areaIds) {
        return new Discovery(Set.copyOf(areaIds));
    }

    public static Discovery of(int... areaIds) {
        TreeSet<Integer> ids = new TreeSet<>();
        for (int id : areaIds) {
            ids.add(id);
        }
        return new Discovery(ids);
    }

    public boolean contains(int areaId) {
        return areaIds.contains(areaId);
    }

    /** @return this discovery if the area is already known, otherwise a new one including it */
    public Discovery with(int areaId) {
        if (contains(areaId)) {
            return this;
        }
        if (!Areas.exists(areaId)) {
            throw new IllegalArgumentException("Unknown area id: " + areaId);
        }
        TreeSet<Integer> ids = new TreeSet<>(areaIds);
        ids.add(areaId);
        return new Discovery(ids);
    }

    public int count() {
        return areaIds.size();
    }

    public boolean isComplete() {
        return count() == Areas.all().size();
    }

    public List<Integer> sortedIds() {
        return List.copyOf(areaIds);
    }
}
