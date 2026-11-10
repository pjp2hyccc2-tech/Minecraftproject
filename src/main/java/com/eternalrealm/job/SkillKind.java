package com.eternalrealm.job;

import com.eternalrealm.core.ModInfo;

/**
 * What a node of a job's skill tree is. Actives and ultimates are used from the skill slots (ultimates only from the
 * ultimate slot); passives work on their own once learned.
 */
public enum SkillKind {
    ACTIVE("active"),
    /** Permanent bonuses that grow with the skill level; never placed in a slot. */
    PASSIVE("passive"),
    /** Big, slow skills; only the ultimate slot ({@link SkillTree#ULTIMATE_SLOT}) takes them. */
    ULTIMATE("ultimate");

    private final String id;

    SkillKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Used from a skill slot (an active or an ultimate). */
    public boolean usable() {
        return this != PASSIVE;
    }

    public String translationKey() {
        return "skill_kind." + ModInfo.MOD_ID + "." + id;
    }
}
