package com.clockworkdiver.customer;

import com.clockworkdiver.clock.ClockType;

import java.util.List;

/** The cast of customers who visit the workshop. Names and lines live in the language files. */
public enum CustomerProfile {
    GREGORY("gregory", 0, 1, 0x1C1C22, List.of(ClockType.POCKET_WATCH)),
    ADA("ada", 1, 0, 0, List.of(ClockType.ASTRONOMICAL, ClockType.ORRERY, ClockType.POCKET_WATCH)),
    BOLT("bolt", 2, 0, 0, List.of(ClockType.STEAM_TOWER, ClockType.POCKET_WATCH, ClockType.CUCKOO)),
    LILY("lily", 3, 0, 0, List.of(ClockType.CUCKOO, ClockType.POCKET_WATCH, ClockType.BIRDCAGE)),
    CELES("celes", 4, 1, 0x2A2F5A, List.of(ClockType.ASTRONOMICAL, ClockType.ORRERY)),
    PHANTOM("phantom", 5, 1, 0x3A1020, List.of(ClockType.BIRDCAGE, ClockType.ORRERY, ClockType.STEAM_TOWER));

    public final String id;
    public final int skin;
    /** 0 = no hat, 1 = top hat. */
    public final int hat;
    public final int hatColor;
    public final List<ClockType> favourites;

    CustomerProfile(String id, int skin, int hat, int hatColor, List<ClockType> favourites) {
        this.id = id;
        this.skin = skin;
        this.hat = hat;
        this.hatColor = hatColor;
        this.favourites = favourites;
    }

    public String nameKey() {
        return "customer.clockworkdiver." + id;
    }

    /** Dialogue line key, e.g. {@code offer.0}, {@code wait}, {@code thanks.1}. */
    public String lineKey(String line) {
        return "dialogue.clockworkdiver." + id + "." + line;
    }

    public static CustomerProfile byId(String id) {
        for (CustomerProfile p : values()) {
            if (p.id.equals(id)) return p;
        }
        return GREGORY;
    }

    public static CustomerProfile byIndex(int index) {
        CustomerProfile[] v = values();
        return v[Math.floorMod(index, v.length)];
    }
}
