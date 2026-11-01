package com.frostbound.core.expedition;

import static com.frostbound.core.resources.ResourceType.COAL;
import static com.frostbound.core.resources.ResourceType.FOOD;
import static com.frostbound.core.resources.ResourceType.IRON;
import static com.frostbound.core.resources.ResourceType.MEDICINE;
import static com.frostbound.core.resources.ResourceType.STEAM_CORE;
import static com.frostbound.core.resources.ResourceType.WOOD;

import com.frostbound.core.resources.Cost;
import com.frostbound.core.util.Rng;
import java.util.Locale;

/** Kinds of places beyond the city (provisional loot tables). */
public enum SiteType {
    ABANDONED_TOWN(0.15),
    OLD_FACTORY(0.20),
    ABANDONED_MINE(0.25),
    GLACIER_CAVE(0.35),
    RESEARCH_FACILITY(0.30),
    SURVIVOR_CAMP(0.10),
    RESOURCE_SITE(0.15);

    public final double danger;

    SiteType(double danger) {
        this.danger = danger;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "frostbound.site." + id();
    }

    public Cost rollLoot(Rng rng, double scale) {
        Cost c = switch (this) {
            case ABANDONED_TOWN -> Cost.of(WOOD, rng.range(40, 90), IRON, rng.range(10, 30), FOOD, rng.range(10, 30),
                    MEDICINE, rng.range(0, 6));
            case OLD_FACTORY -> Cost.of(IRON, rng.range(40, 80), COAL, rng.range(20, 50));
            case ABANDONED_MINE -> Cost.of(COAL, rng.range(60, 140), IRON, rng.range(10, 30));
            case GLACIER_CAVE -> Cost.of(STEAM_CORE, rng.range(1, 2), IRON, rng.range(10, 20));
            case RESEARCH_FACILITY -> Cost.of(IRON, rng.range(10, 25), MEDICINE, rng.range(5, 12));
            case SURVIVOR_CAMP -> Cost.of(FOOD, rng.range(5, 15));
            case RESOURCE_SITE -> Cost.of(COAL, rng.range(40, 80), WOOD, rng.range(40, 80));
        };
        return c.scaled(scale);
    }
}
