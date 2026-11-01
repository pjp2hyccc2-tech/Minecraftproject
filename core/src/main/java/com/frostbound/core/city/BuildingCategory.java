package com.frostbound.core.city;

import java.util.Locale;

/** Building categories, also used for the BUILD menu tabs and structure palettes. */
public enum BuildingCategory {
    HOUSING,
    RESOURCE,
    FOOD,
    MEDICAL,
    INDUSTRY,
    SOCIAL,
    SPECIAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "frostbound.category." + id();
    }
}
