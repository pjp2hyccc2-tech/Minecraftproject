package com.eternalrealm.job;

import com.eternalrealm.core.ModInfo;

import java.util.Optional;

/**
 * The jobs a character can have. {@link #id()} is the stable persistence/network key: never rename it.
 * {@link #NONE} is the state before the first choice.
 */
public enum JobType {
    NONE("none"),
    /** ナイト: tank, guards and protects the party. */
    KNIGHT("knight"),
    /** アサシン: fast critical melee damage. */
    ASSASSIN("assassin"),
    /** メイジ: ranged magic damage. */
    MAGE("mage"),
    /** レンジャー: bows, mobility. */
    RANGER("ranger"),
    /** プリースト: healing and support. */
    PRIEST("priest"),
    /** 魔剣士: swords empowered by magic. */
    SPELLBLADE("spellblade");

    private final String id;

    JobType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "job." + ModInfo.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    public boolean isChosen() {
        return this != NONE;
    }

    public static Optional<JobType> byId(String id) {
        for (JobType type : values()) {
            if (type.id.equals(id)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /** The job of a stored id; unknown ids (e.g. a job removed by an update) count as {@link #NONE}. */
    public static JobType fromStored(String id) {
        return byId(id).orElse(NONE);
    }
}
