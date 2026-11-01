package com.frostbound.core.population;

import java.util.Locale;

/**
 * What a citizen is trained for. Specialists can also do general labor, but laborers cannot take
 * specialist jobs. Being without a job ("unemployed") is an assignment state, not a profession.
 */
public enum Profession {
    WORKER,
    TECHNICIAN,
    MEDIC,
    SCOUT,
    ENGINEER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "frostbound.profession." + id();
    }

    /** Whether someone with this profession may take a job that asks for {@code job}. */
    public boolean canWork(Profession job) {
        return job == this || job == WORKER;
    }
}
