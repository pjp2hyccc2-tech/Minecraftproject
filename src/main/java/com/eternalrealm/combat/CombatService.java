package com.eternalrealm.combat;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The fighters of one logical server, keyed by player UUID. Server thread only.
 *
 * <p>Skill cooldowns outlive a fighter's removal for the rest of the server session, so logging out and back in does not
 * reset them (they are not persisted across restarts).</p>
 */
public final class CombatService {
    private final Map<UUID, Combatant> combatants = new LinkedHashMap<>();
    private final Map<UUID, Combatant> view = Collections.unmodifiableMap(combatants);
    private final Map<UUID, Cooldown> rememberedCooldowns = new HashMap<>();

    private record Cooldown(long readyAt, int total) {
    }

    /** Creates the fighter (restoring a remembered skill cooldown), or updates the stats of an existing one. */
    public Combatant register(UUID playerId, CombatStats stats, long now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(stats, "stats");
        Combatant existing = combatants.get(playerId);
        if (existing != null) {
            existing.setStats(stats);
            return existing;
        }
        Combatant created = new Combatant(stats);
        Cooldown cooldown = rememberedCooldowns.remove(playerId);
        if (cooldown != null && cooldown.readyAt() > now) {
            created.setSkillCooldown(now, (int) Math.min(Integer.MAX_VALUE, cooldown.readyAt() - now), cooldown.total());
        }
        combatants.put(playerId, created);
        return created;
    }

    public Optional<Combatant> get(UUID playerId) {
        return Optional.ofNullable(combatants.get(playerId));
    }

    /** Removes the fighter, remembering a running skill cooldown. */
    public void remove(UUID playerId, long now) {
        Combatant removed = combatants.remove(playerId);
        rememberedCooldowns.values().removeIf(cooldown -> cooldown.readyAt() <= now);
        if (removed != null) {
            int remaining = removed.skillCooldownRemaining(now);
            if (remaining > 0) {
                rememberedCooldowns.put(playerId, new Cooldown(now + remaining, removed.skillCooldownTotal()));
            }
        }
    }

    /** Live read-only view, iteration in registration order. Do not register/remove while iterating it. */
    public Map<UUID, Combatant> all() {
        return view;
    }

    public int size() {
        return combatants.size();
    }
}
