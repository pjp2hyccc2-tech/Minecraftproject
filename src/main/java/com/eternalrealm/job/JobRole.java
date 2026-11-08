package com.eternalrealm.job;

import com.eternalrealm.core.ModInfo;

/** What a job does in a party (shown in party/raid UIs; bosses use it for targeting). */
public enum JobRole {
    NONE("none"),
    TANK("tank"),
    MELEE("melee"),
    CASTER("caster"),
    RANGED("ranged"),
    HEALER("healer"),
    HYBRID("hybrid");

    private final String id;

    JobRole(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "job_role." + ModInfo.MOD_ID + "." + id;
    }
}
