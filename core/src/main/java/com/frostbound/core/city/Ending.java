package com.frostbound.core.city;

import java.util.Locale;

/** The five endings. */
public enum Ending {
    HOPE_OF_HUMANITY,
    CITY_OF_STEEL,
    LAST_FLAME,
    REBIRTH,
    ETERNAL_WINTER;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String titleKey() {
        return "frostbound.ending." + id() + ".title";
    }

    public String textKey() {
        return "frostbound.ending." + id() + ".text";
    }
}
