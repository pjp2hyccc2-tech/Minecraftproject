package com.frostbound.core.society;

import com.frostbound.core.city.Modifiers;
import com.frostbound.core.resources.Cost;
import java.util.List;
import java.util.function.Consumer;

/** A law (or, for the emergency shift, a repeatable decree). Provisional values. */
public final class Law {
    public final String id;
    public final List<String> requires;
    public final List<String> conflicts;
    /** Immediate change to every citizen's hope and discontent when enacted. */
    public final double hopeNow;
    public final double discontentNow;
    public final Cost dailyCost;
    /** Repeatable decree instead of a permanent law. */
    public final boolean decree;
    final Consumer<Modifiers> effect;

    Law(String id, List<String> requires, List<String> conflicts, double hopeNow, double discontentNow, Cost dailyCost,
            boolean decree, Consumer<Modifiers> effect) {
        this.id = id;
        this.requires = requires;
        this.conflicts = conflicts;
        this.hopeNow = hopeNow;
        this.discontentNow = discontentNow;
        this.dailyCost = dailyCost;
        this.decree = decree;
        this.effect = effect;
    }

    public void apply(Modifiers m) {
        effect.accept(m);
    }

    public String key() {
        return "frostbound.law." + id;
    }
}
