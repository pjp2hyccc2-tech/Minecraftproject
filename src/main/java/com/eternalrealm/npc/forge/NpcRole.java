package com.eternalrealm.npc.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Optional;

/** The town's service NPCs. {@link #id()} is stored on the entity: never rename it. */
public enum NpcRole {
    QUEST_BOARD("quest_board", "librarian"),
    ITEM_SHOP("item_shop", "cleric"),
    BLACKSMITH("blacksmith", "weaponsmith"),
    JOB_MASTER("job_master", "armorer"),
    AUCTION("auction", "cartographer"),
    MAILBOX("mailbox", "shepherd"),
    GUILD_REGISTRAR("guild_registrar", "toolsmith"),
    GATEKEEPER("gatekeeper", "mason"),
    ARENA_MASTER("arena_master", "butcher"),
    HALL_OF_FAME("hall_of_fame", "librarian"),
    /** Sells mounts (docs/modules/mounts.md). */
    STABLE_MASTER("stable_master", "leatherworker"),
    /** Story characters: only talk. */
    STORY("story", "nitwit"),
    /** Sells the guild houses on the Guild Gate Terrace (docs/modules/guildhouse.md). */
    GUILD_ESTATES("guild_estates", "librarian");

    private final String id;
    private final String profession;

    NpcRole(String id, String profession) {
        this.id = id;
        this.profession = profession;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "npc." + ModInfo.MOD_ID + "." + id;
    }

    /** Vanilla profession whose look the NPC wears. */
    public VillagerProfession profession() {
        return switch (profession) {
            case "librarian" -> VillagerProfession.LIBRARIAN;
            case "cleric" -> VillagerProfession.CLERIC;
            case "weaponsmith" -> VillagerProfession.WEAPONSMITH;
            case "armorer" -> VillagerProfession.ARMORER;
            case "cartographer" -> VillagerProfession.CARTOGRAPHER;
            case "shepherd" -> VillagerProfession.SHEPHERD;
            case "toolsmith" -> VillagerProfession.TOOLSMITH;
            case "mason" -> VillagerProfession.MASON;
            case "butcher" -> VillagerProfession.BUTCHER;
            case "leatherworker" -> VillagerProfession.LEATHERWORKER;
            default -> VillagerProfession.NITWIT;
        };
    }

    public static Optional<NpcRole> byId(String id) {
        for (NpcRole role : values()) {
            if (role.id.equals(id)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
