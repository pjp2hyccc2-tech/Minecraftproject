package com.frostbound.core.resources;

import java.util.Locale;

/** The six stockpiled resources. */
public enum ResourceType {
    COAL,
    WOOD,
    IRON,
    FOOD,
    MEDICINE,
    STEAM_CORE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String key() {
        return "frostbound.resource." + id();
    }

    public static ResourceType byId(String id) {
        for (ResourceType t : values()) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }
}
