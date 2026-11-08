package com.eternalrealm.monster;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;

/**
 * How a monster template is used. The role decides the baseline durability the catalog tunes against
 * (docs/modules/world.md): a level-matched player needs about 4 light attacks for a NORMAL monster, about three times as
 * many for an ELITE and about 2 for a MINION.
 */
public enum MonsterRole {
    /** Field and dungeon-wave monster. */
    NORMAL,
    /** Rare, stronger variant (field elites, dungeon wave captains) with better loot. */
    ELITE,
    /** Summoned by a boss; weak, little EXP and no loot so that summons cannot be farmed. */
    MINION;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "monster_role." + ModInfo.MOD_ID + "." + id();
    }

    /** Name plate format of this role. Args: level, monster name. */
    public String nameplateKey() {
        return "hud." + ModInfo.MOD_ID + ".monster.nameplate." + id();
    }
}
