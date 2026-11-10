package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.JobType;

import java.util.List;
import java.util.Locale;

/**
 * The cut of an armor set and the jobs it suits: heavy plate (knight, spellblade), light leather (assassin, ranger) and
 * robes (mage, priest). Anyone may wear any set; the style shapes the stats.
 *
 * @param defense multiplies the base defense of the pieces
 * @param health  multiplies the base max health of the pieces
 */
public enum ArmorStyle {
    HEAVY(1.12, 1.05, List.of(JobType.KNIGHT, JobType.SPELLBLADE)),
    LIGHT(0.92, 0.95, List.of(JobType.ASSASSIN, JobType.RANGER)),
    ROBE(0.76, 0.90, List.of(JobType.MAGE, JobType.PRIEST));

    private final double defense;
    private final double health;
    private final List<JobType> jobs;

    ArmorStyle(double defense, double health, List<JobType> jobs) {
        this.defense = defense;
        this.health = health;
        this.jobs = jobs;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "armor_style." + ModInfo.MOD_ID + "." + id();
    }

    public double defense() {
        return defense;
    }

    public double health() {
        return health;
    }

    /** The jobs the style is cut for. */
    public List<JobType> jobs() {
        return jobs;
    }
}
