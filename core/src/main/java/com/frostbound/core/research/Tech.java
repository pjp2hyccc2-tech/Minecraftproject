package com.frostbound.core.research;

import com.frostbound.core.city.Modifiers;
import com.frostbound.core.resources.Cost;
import java.util.List;
import java.util.function.Consumer;

/** One research node. */
public final class Tech {
    public final String id;
    public final String category;
    public final int tier;
    public final int points;
    public final Cost cost;
    public final List<String> requires;
    public final List<String> unlocks;
    final Consumer<Modifiers> effect;

    Tech(String id, String category, int tier, int points, Cost cost, List<String> requires, List<String> unlocks,
            Consumer<Modifiers> effect) {
        this.id = id;
        this.category = category;
        this.tier = tier;
        this.points = points;
        this.cost = cost;
        this.requires = requires;
        this.unlocks = unlocks;
        this.effect = effect;
    }

    public void apply(Modifiers m) {
        m.unlocked.addAll(unlocks);
        effect.accept(m);
    }

    public String key() {
        return "frostbound.tech." + id;
    }

    public String descKey() {
        return "frostbound.tech." + id + ".desc";
    }
}
