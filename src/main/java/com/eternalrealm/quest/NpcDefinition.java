package com.eternalrealm.quest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.world.Areas;

/**
 * A quest NPC. The Forge layer places one per id (a villager-like entity tagged with the id) in its area and reports
 * {@link GameEvent.NpcTalked} when a player interacts with it.
 *
 * @param areaId area the NPC stands in
 */
public record NpcDefinition(String id, int areaId) {
    public NpcDefinition {
        Check.id("id", id);
        Check.range("areaId", areaId, Areas.FIRST_ID, Areas.LAST_ID);
    }

    public String translationKey() {
        return "npc." + ModInfo.MOD_ID + "." + id;
    }
}
