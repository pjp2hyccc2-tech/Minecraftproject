package com.clockworkdiver.customer;

import com.clockworkdiver.clock.ClockType;

import java.util.ArrayList;
import java.util.List;

/** The cast of customers who visit the workshop. Names and lines live in the language files. */
public enum CustomerProfile {
    GREGORY("gregory", 0, 1, 0x55525E, false, List.of(ClockType.POCKET_WATCH)),
    ADA("ada", 1, 0, 0, true, List.of(ClockType.ASTRONOMICAL, ClockType.ORRERY, ClockType.POCKET_WATCH)),
    BOLT("bolt", 2, 0, 0, true, List.of(ClockType.STEAM_TOWER, ClockType.POCKET_WATCH, ClockType.CUCKOO)),
    LILY("lily", 3, 0, 0, true, List.of(ClockType.CUCKOO, ClockType.POCKET_WATCH, ClockType.BIRDCAGE, ClockType.MUSIC_BOX)),
    CELES("celes", 4, 1, 0x5A68B8, true, List.of(ClockType.ASTRONOMICAL, ClockType.ORRERY)),
    PHANTOM("phantom", 5, 1, 0x8A3048, true, List.of(ClockType.BIRDCAGE, ClockType.ORRERY, ClockType.STEAM_TOWER, ClockType.MUSIC_BOX)),
    /** Bell-ringer apprentice of the Great Clock tower. */
    PIP("pip", 6, 0, 0, true, List.of(ClockType.GRANDFATHER, ClockType.STEAM_TOWER)),
    /** Viktor before he gives his name. */
    STRANGER("stranger", 7, 1, 0x4A4A52, false, List.of()),
    VIKTOR("viktor", 7, 1, 0x4A4A52, false, List.of()),
    /** The vanished master. */
    ELIAS("elias", 8, 1, 0x3A2A1E, false, List.of());

    public final String id;
    public final int skin;
    /** 0 = no hat, 1 = top hat. */
    public final int hat;
    public final int hatColor;
    /** Brings ordinary commissions (story-only characters do not). */
    public final boolean regular;
    public final List<ClockType> favourites;

    CustomerProfile(String id, int skin, int hat, int hatColor, boolean regular, List<ClockType> favourites) {
        this.id = id;
        this.skin = skin;
        this.hat = hat;
        this.hatColor = hatColor;
        this.regular = regular;
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

    /** Is {@code id} one of the cast (used to tell speakers from special dialogue voices)? */
    public static boolean exists(String id) {
        for (CustomerProfile p : values()) {
            if (p.id.equals(id)) return true;
        }
        return false;
    }

    public static List<CustomerProfile> regulars() {
        List<CustomerProfile> out = new ArrayList<>();
        for (CustomerProfile p : values()) if (p.regular) out.add(p);
        return out;
    }
}
