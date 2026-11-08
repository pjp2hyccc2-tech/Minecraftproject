package com.eternalrealm.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The fighters of one logical server, keyed by player UUID. Server thread only.
 */
public final class CombatService {
    private final Map<UUID, Combatant> combatants = new LinkedHashMap<>();
    private final Map<UUID, Combatant> view = Collections.unmodifiableMap(combatants);

    /** Creates the fighter, or updates the stats of an existing one (keeping its state). */
    public Combatant register(UUID playerId, CombatStats stats) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(stats, "stats");
        Combatant existing = combatants.get(playerId);
        if (existing != null) {
            existing.setStats(stats);
            return existing;
        }
        Combatant created = new Combatant(stats);
        combatants.put(playerId, created);
        return created;
    }

    public Optional<Combatant> get(UUID playerId) {
        return Optional.ofNullable(combatants.get(playerId));
    }

    public void remove(UUID playerId) {
        combatants.remove(playerId);
    }

    /** Live read-only view, iteration in registration order. Do not register/remove while iterating it. */
    public Map<UUID, Combatant> all() {
        return view;
    }

    public int size() {
        return combatants.size();
    }
}
