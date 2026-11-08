package com.eternalrealm.story;

import java.util.Optional;

/**
 * Everyone the players can talk to ({@link StoryTrigger.NpcTalk}). Ids are fixed:
 * <ul>
 *     <li>{@link Kind#SERVICE}: the town NPC roles ({@code NpcRole} ids); a line for every story progress 0..9</li>
 *     <li>{@link Kind#CHARACTER}: talk-only story characters in the capital ({@code NpcRole.STORY} NPCs whose entity
 *         carries this id); a line for every progress from {@link #firstProgress()} on, and absent before it</li>
 *     <li>{@link Kind#QUEST_GIVER}: the quest module's story NPCs ({@code npc.eternalrealm.<id>}); dialogues per quest
 *         ({@link StoryTrigger.QuestTalk}) plus a few progress lines when there is no quest to talk about</li>
 * </ul>
 */
public enum StoryNpc {
    QUEST_BOARD("quest_board", Speaker.MINA, Kind.SERVICE, StoryArea.ARCELIA, 0),
    ITEM_SHOP("item_shop", Speaker.PIM, Kind.SERVICE, StoryArea.ARCELIA, 0),
    BLACKSMITH("blacksmith", Speaker.GORAN, Kind.SERVICE, StoryArea.ARCELIA, 0),
    JOB_MASTER("job_master", Speaker.HAGEN, Kind.SERVICE, StoryArea.ARCELIA, 0),
    AUCTION("auction", Speaker.LUCE, Kind.SERVICE, StoryArea.ARCELIA, 0),
    MAILBOX("mailbox", Speaker.CHICO, Kind.SERVICE, StoryArea.ARCELIA, 0),
    GUILD_REGISTRAR("guild_registrar", Speaker.MARTA, Kind.SERVICE, StoryArea.ARCELIA, 0),
    GATEKEEPER("gatekeeper", Speaker.GANT, Kind.SERVICE, StoryArea.ARCELIA, 0),
    ARENA_MASTER("arena_master", Speaker.ZAIN, Kind.SERVICE, StoryArea.ARCELIA, 0),
    HALL_OF_FAME("hall_of_fame", Speaker.CHLOE, Kind.SERVICE, StoryArea.ARCELIA, 0),

    /** In the castle's Hall of the Star Gate (or at the guild, badly disguised as "Lili"). */
    LILIA("lilia", Speaker.LILIA, Kind.CHARACTER, StoryArea.ARCELIA, 0),
    /** In the guild hall. */
    GUSTAV("gustav", Speaker.GUSTAV, Kind.CHARACTER, StoryArea.ARCELIA, 0),
    /** In the observatory / library. */
    NOX("nox", Speaker.NOX, Kind.CHARACTER, StoryArea.ARCELIA, 0),
    /** Washing dishes in the guild kitchen once b1 has fallen. */
    LASZLO("laszlo", Speaker.LASZLO, Kind.CHARACTER, StoryArea.ARCELIA, 1),
    /** Recovering in the guild infirmary once b4 has fallen. */
    WOLFRAM("wolfram", Speaker.WOLFRAM, Kind.CHARACTER, StoryArea.ARCELIA, 4),

    ARCELIA_KNIGHT_CAPTAIN("arcelia_knight_captain", Speaker.ADEL, Kind.QUEST_GIVER, StoryArea.ARCELIA, 0),
    ELDER_FOREST_SAGE("elder_forest_sage", Speaker.NERIS, Kind.QUEST_GIVER, StoryArea.ELDER_FOREST, 1),
    SANDRIA_CARAVAN_MASTER("sandria_caravan_master", Speaker.KHALID, Kind.QUEST_GIVER, StoryArea.SANDRIA_DESERT, 2),
    NORDIA_CHIEFTAIN("nordia_chieftain", Speaker.HELGA, Kind.QUEST_GIVER, StoryArea.NORDIA_SNOWFIELD, 3),
    DRAGONIA_BLACKSMITH("dragonia_blacksmith", Speaker.ILSA, Kind.QUEST_GIVER, StoryArea.DRAGONIA_VOLCANO, 5),
    CELESTIA_ORACLE("celestia_oracle", Speaker.SELENE, Kind.QUEST_GIVER, StoryArea.CELESTIA, 6),
    TOWER_WATCHER("tower_watcher", Speaker.GUNTER, Kind.QUEST_GIVER, StoryArea.TOWER_OF_THE_END, 7);

    public enum Kind {
        SERVICE,
        CHARACTER,
        QUEST_GIVER
    }

    private final String id;
    private final Speaker speaker;
    private final Kind kind;
    private final StoryArea area;
    private final int firstProgress;

    StoryNpc(String id, Speaker speaker, Kind kind, StoryArea area, int firstProgress) {
        this.id = id;
        this.speaker = speaker;
        this.kind = kind;
        this.area = area;
        this.firstProgress = firstProgress;
    }

    public String id() {
        return id;
    }

    /** Speaker of this NPC's lines. */
    public Speaker speaker() {
        return speaker;
    }

    public Kind kind() {
        return kind;
    }

    /** Where the NPC stands. */
    public StoryArea area() {
        return area;
    }

    /** Story progress of the NPC's first progress line (for quest givers: when their area normally opens). */
    public int firstProgress() {
        return firstProgress;
    }

    /** Whether the NPC should be in the world: story characters appear at {@link #firstProgress()}, others always. */
    public boolean isPresentAt(int storyProgress) {
        return kind != Kind.CHARACTER || storyProgress >= firstProgress;
    }

    public static Optional<StoryNpc> byId(String id) {
        for (StoryNpc npc : values()) {
            if (npc.id.equals(id)) {
                return Optional.of(npc);
            }
        }
        return Optional.empty();
    }
}
