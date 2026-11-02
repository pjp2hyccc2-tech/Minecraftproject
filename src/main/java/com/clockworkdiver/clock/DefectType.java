package com.clockworkdiver.clock;

/** What is wrong with a part, which decides the repair challenge waiting inside the clock. */
public enum DefectType {
    /** Rust has seized a gear: smash the rust clusters while rust mites swarm out of them. */
    RUST("rust", new String[]{"barrel_gear", "center_wheel", "third_wheel", "fourth_wheel"}),
    /** Teeth were stolen from a wheel by cog gremlins: defeat them and fit the teeth back into the sockets. */
    TEETH("teeth", new String[]{"escape_wheel", "minute_wheel", "hour_wheel"}),
    /** A giant screw came loose: climb its thread and turn the head in time with the beat. */
    SCREW("screw", new String[]{"bridge_screw", "case_screw", "regulator_screw"}),
    /** The spring ran down: wind the crank while defending it from waves of vermin. */
    SPRING("spring", new String[]{"mainspring", "hairspring"}),
    /** A Time Eater nests in the escapement: defeat it. */
    NEST("nest", new String[]{"balance_wheel", "pallet_fork"});

    public final String id;
    public final String[] parts;

    DefectType(String id, String[] parts) {
        this.id = id;
        this.parts = parts;
    }

    public String nameKey() {
        return "defect.clockworkdiver." + id;
    }

    public String objectiveKey() {
        return "objective.clockworkdiver.dive." + id;
    }

    public static String partKey(String part) {
        return "part.clockworkdiver." + part;
    }

    public static DefectType byId(String id) {
        for (DefectType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return RUST;
    }
}
