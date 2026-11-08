package com.eternalrealm.entity;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The four enemy rigs; each is one entity type whose synced variant selects texture, scale and look. */
public enum EnemyRig {
    HUMANOID, BEAST, GOLEM, FLYER;

    /** "humanoid", "beast", "golem" or "flyer". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Variant ids of this rig, the first one is the default. */
    public List<String> variants() {
        return RigData.variants(id());
    }

    public static Optional<EnemyRig> byId(String id) {
        for (EnemyRig rig : values()) {
            if (rig.id().equals(id)) {
                return Optional.of(rig);
            }
        }
        return Optional.empty();
    }
}
