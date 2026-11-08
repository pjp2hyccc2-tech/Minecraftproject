package com.eternalrealm.story;

import static com.eternalrealm.story.EndingSequence.CreditKind.CREDIT;
import static com.eternalrealm.story.EndingSequence.CreditKind.HEADING;
import static com.eternalrealm.story.EndingSequence.CreditKind.PLAYERS;
import static com.eternalrealm.story.EndingSequence.Display.CHAT;
import static com.eternalrealm.story.EndingSequence.Display.TITLE;
import static com.eternalrealm.story.Speaker.*;

/**
 * The story content: which dialogues exist and who speaks each line, in story order. The texts are in
 * docs/i18n/story.json under the keys {@link StoryCatalog} derives from the ids (line n of dialogue
 * {@code boss.b1.intro} is {@code story.eternalrealm.boss.b1.intro.n}). Edit both together: adding a line here
 * needs its text there, and the story tests fail on any missing or stale key. The plot is in docs/STORY.md.
 */
final class StoryScript {
    private StoryScript() {
    }

    static StoryCatalog.Builder content() {
        StoryCatalog.Builder b = new StoryCatalog.Builder();
        prologue(b);
        areas(b);
        dungeons(b);
        bosses(b);
        finalPhases(b);
        ending(b);
        epilogues(b);
        days(b);
        townNpcs(b);
        storyCharacters(b);
        questGiverLines(b);
        quests(b);
        return b;
    }

    /** Prologue (first login of the season). */
    private static void prologue(StoryCatalog.Builder b) {
        b.dialogue("prologue", NARRATOR, LILIA, NARRATOR, LILIA, LILIA, LILIA, LILIA, LILIA, LILIA, NOX, LILIA, NOX,
                LILIA, NARRATOR);
    }

    /** Areas: title cards, first visit, unlock (opened by the first kill of the previous boss). */
    private static void areas(StoryCatalog.Builder b) {
        b.areaCard(StoryArea.ARCELIA);
        b.dialogue("area.arcelia.enter", NARRATOR, NARRATOR);
        b.areaCard(StoryArea.ELDER_FOREST);
        b.dialogue("area.elder_forest.enter", NARRATOR, NARRATOR);
        b.dialogue("area.elder_forest.unlocked", KING, LILIA);
        b.areaCard(StoryArea.SANDRIA_DESERT);
        b.dialogue("area.sandria_desert.enter", NARRATOR, NARRATOR);
        b.dialogue("area.sandria_desert.unlocked", LILIA, GUSTAV);
        b.areaCard(StoryArea.NORDIA_SNOWFIELD);
        b.dialogue("area.nordia_snowfield.enter", NARRATOR, NARRATOR);
        b.dialogue("area.nordia_snowfield.unlocked", LILIA, GUSTAV);
        b.areaCard(StoryArea.DRAGONIA_VOLCANO);
        b.dialogue("area.dragonia_volcano.enter", NARRATOR, NARRATOR);
        b.dialogue("area.dragonia_volcano.unlocked", LILIA, KING, LILIA, ADEL);
        b.areaCard(StoryArea.CELESTIA);
        b.dialogue("area.celestia.enter", NARRATOR, NARRATOR);
        b.dialogue("area.celestia.unlocked", LILIA, NOX);
        b.areaCard(StoryArea.TOWER_OF_THE_END);
        b.dialogue("area.tower_of_the_end.enter", NARRATOR, NARRATOR);
        b.dialogue("area.tower_of_the_end.unlocked", NARRATOR, LILIA, NOX);
    }

    /** Dungeons (start of a run; "final" is the raid). */
    private static void dungeons(StoryCatalog.Builder b) {
        b.dialogue("dungeon.d1.enter", NARRATOR, BANDIT, NARRATOR);
        b.dialogue("dungeon.d2.enter", NARRATOR, NARRATOR);
        b.dialogue("dungeon.d3.enter", NARRATOR, CULTIST);
        b.dialogue("dungeon.d4.enter", NARRATOR, NARRATOR);
        b.dialogue("dungeon.d5.enter", NARRATOR, INGRID);
        b.dialogue("dungeon.d6.enter", NARRATOR, NARRATOR);
        b.dialogue("dungeon.d7.enter", NARRATOR, ORDO);
        b.dialogue("dungeon.d8.enter", NARRATOR, NARRATOR);
        b.dialogue("dungeon.final.enter", NARRATOR, NARRATOR);
    }

    /** Bosses: intro, defeat (later kills), first_defeat (first server kill: a Star Memory), phase barks. */
    private static void bosses(StoryCatalog.Builder b) {
        b.dialogue("boss.b1.intro", LASZLO, LASZLO, LASZLO);
        b.dialogue("boss.b1.defeat", LASZLO);
        b.dialogue("boss.b1.first_defeat", LASZLO, NARRATOR, NARRATOR, ELMINA, KANATA, NOX, NOX);
        b.bark("b1", 2, LASZLO);
        b.dialogue("boss.b2.intro", GUARDIAN, GUARDIAN, GUARDIAN);
        b.dialogue("boss.b2.defeat", GUARDIAN);
        b.dialogue("boss.b2.first_defeat", GUARDIAN, GUARDIAN, NARRATOR, GLAUS, ELMINA, YOUNG_MAGE, KANATA, NARRATOR);
        b.bark("b2", 2, GUARDIAN);
        b.bark("b2", 3, GUARDIAN);
        b.dialogue("boss.b3.intro", ZAHRA, ZAHRA, ZAHRA);
        b.dialogue("boss.b3.defeat", NARRATOR);
        b.dialogue("boss.b3.first_defeat", ZAHRA, ZAHRA, NARRATOR, ELMINA, KANATA, ELMINA, NOX);
        b.bark("b3", 2, ZAHRA);
        b.bark("b3", 3, ZAHRA);
        b.dialogue("boss.b4.intro", EXECUTIONER, EXECUTIONER);
        b.dialogue("boss.b4.defeat", EXECUTIONER);
        b.dialogue("boss.b4.first_defeat", EXECUTIONER, NARRATOR, WOLFRAM, NARRATOR, GLAUS, KANATA, WOLFRAM);
        b.bark("b4", 2, EXECUTIONER);
        b.bark("b4", 3, EXECUTIONER);
        b.dialogue("boss.b5.intro", INGRID, INGRID, INGRID);
        b.dialogue("boss.b5.defeat", INGRID);
        b.dialogue("boss.b5.first_defeat", INGRID, INGRID, NARRATOR, INGRID, KANATA, INGRID, INGRID, NARRATOR);
        b.bark("b5", 2, INGRID);
        b.bark("b5", 3, INGRID);
        b.dialogue("boss.b6.intro", AZRAG, AZRAG, AZRAG);
        b.dialogue("boss.b6.defeat", AZRAG);
        b.dialogue("boss.b6.first_defeat", AZRAG, AZRAG, NARRATOR, KANATA, AZRAG, AZRAG);
        b.bark("b6", 2, AZRAG);
        b.bark("b6", 3, AZRAG);
        b.dialogue("boss.b7.intro", ORDO, ORDO);
        b.dialogue("boss.b7.defeat", ORDO);
        b.dialogue("boss.b7.first_defeat", ORDO, NARRATOR, YOUNG_MAGE, KANATA, YOUNG_MAGE, NOX, NOX);
        b.bark("b7", 2, ORDO);
        b.bark("b7", 3, ORDO);
        b.dialogue("boss.b8.intro", END_GATEKEEPER, END_GATEKEEPER);
        b.dialogue("boss.b8.defeat", END_GATEKEEPER);
        b.dialogue("boss.b8.first_defeat", END_GATEKEEPER, GLAUS, NOX, GLAUS, NARRATOR, ELMINA, KANATA, KANATA, GLAUS);
        b.bark("b8", 2, END_GATEKEEPER);
        b.bark("b8", 3, END_GATEKEEPER);
        b.dialogue("boss.final.intro", END_KING, END_KING, END_KING);
        b.dialogue("boss.final.defeat", END_KING);
        b.dialogue("boss.final.first_defeat", END_KING, NARRATOR);
    }

    /** The King of the End: phase lines (phase 1 = start of the fight). */
    private static void finalPhases(StoryCatalog.Builder b) {
        b.dialogue("final.phase1", NARRATOR, END_KING);
        b.dialogue("final.phase2", END_KING, NARRATOR);
        b.dialogue("final.phase3", END_KING, LILIA);
        b.dialogue("final.phase4", END_KING, KANATA, KANATA);
    }

    /** Ending: about a minute of timed lines (seconds), then the staff roll. */
    private static void ending(StoryCatalog.Builder b) {
        b.beat(0, NARRATOR, CHAT);
        b.beat(3, NARRATOR, CHAT);
        b.beat(6, KANATA, CHAT);
        b.beat(9, KANATA, CHAT);
        b.beat(12, NOX, CHAT);
        b.beat(15, KANATA, CHAT);
        b.beat(18, NOX, CHAT);
        b.beat(21, KANATA, CHAT);
        b.beat(24, KANATA, CHAT);
        b.beat(27, KANATA, CHAT);
        b.beat(30, NARRATOR, CHAT);
        b.beat(33, NARRATOR, CHAT);
        b.beat(36, NARRATOR, TITLE);
        b.beat(39, LILIA, CHAT);
        b.beat(42, KING, CHAT);
        b.beat(45, GUSTAV, CHAT);
        b.beat(48, LASZLO, CHAT);
        b.beat(51, GUSTAV, CHAT);
        b.beat(54, LILIA, CHAT);
        b.beat(57, LILIA, CHAT);
        b.beat(60, NARRATOR, TITLE);

        b.credit(HEADING, "title");
        b.credit(CREDIT, "subtitle");
        b.credit(HEADING, "cast");
        b.credit(CREDIT, "lilia");
        b.credit(CREDIT, "nox");
        b.credit(CREDIT, "gustav");
        b.credit(CREDIT, "adel");
        b.credit(CREDIT, "king");
        b.credit(CREDIT, "laszlo");
        b.credit(CREDIT, "zahra");
        b.credit(CREDIT, "wolfram");
        b.credit(CREDIT, "ingrid");
        b.credit(CREDIT, "azrag");
        b.credit(CREDIT, "ordo");
        b.credit(CREDIT, "glaus");
        b.credit(CREDIT, "elmina");
        b.credit(CREDIT, "kanata");
        b.credit(HEADING, "travelers");
        b.credit(CREDIT, "travelers_list");
        b.credit(HEADING, "townsfolk");
        b.credit(CREDIT, "townsfolk1");
        b.credit(CREDIT, "townsfolk2");
        b.credit(PLAYERS, "heroes");
        b.credit(HEADING, "production");
        b.credit(CREDIT, "team");
        b.credit(HEADING, "thanks");
        b.credit(CREDIT, "thanks_all");
        b.credit(CREDIT, "end");
    }

    /** Epilogues: after the staff roll / at the season end (victory), or the season end without victory. */
    private static void epilogues(StoryCatalog.Builder b) {
        b.dialogue("epilogue.victory", NARRATOR, NARRATOR, LILIA, GUSTAV, NOX, LILIA, LILIA, NARRATOR, NARRATOR, NOX,
                NARRATOR);
        b.dialogue("epilogue.unfinished", NARRATOR, LILIA, LILIA, NOX, LILIA, NARRATOR);
    }

    /** Days (season days 1..12, the eve and the final day of any length, extra days, after the victory). */
    private static void days(StoryCatalog.Builder b) {
        b.dialogue("day.1", NARRATOR, LILIA);
        b.dialogue("day.2", NARRATOR, GUSTAV);
        b.dialogue("day.3", NOX);
        b.dialogue("day.4", NARRATOR, MINA);
        b.dialogue("day.5", LILIA);
        b.dialogue("day.6", NARRATOR, GUSTAV);
        b.dialogue("day.7", NARRATOR, NOX);
        b.dialogue("day.8", NARRATOR, CHICO);
        b.dialogue("day.9", LILIA);
        b.dialogue("day.10", NARRATOR, NOX);
        b.dialogue("day.11", GUSTAV);
        b.dialogue("day.12", NARRATOR, LILIA);
        b.dialogue("day.eve", NARRATOR, NOX);
        b.dialogue("day.final", NARRATOR, LILIA);
        b.dialogue("day.extra", NARRATOR, NOX);
        b.dialogue("day.peace", NARRATOR, LILIA);
        b.dialogue("day.farewell", NARRATOR, LILIA);
    }

    /** Town NPC roles: one dialogue per story progress 0..9. */
    private static void townNpcs(StoryCatalog.Builder b) {
        b.npc(StoryNpc.QUEST_BOARD, 0, 2);
        b.npc(StoryNpc.QUEST_BOARD, 1, 2);
        b.npc(StoryNpc.QUEST_BOARD, 2, 1);
        b.npc(StoryNpc.QUEST_BOARD, 3, 2);
        b.npc(StoryNpc.QUEST_BOARD, 4, 1);
        b.npc(StoryNpc.QUEST_BOARD, 5, 1);
        b.npc(StoryNpc.QUEST_BOARD, 6, 1);
        b.npc(StoryNpc.QUEST_BOARD, 7, 1);
        b.npc(StoryNpc.QUEST_BOARD, 8, 1);
        b.npc(StoryNpc.QUEST_BOARD, 9, 1);

        b.npc(StoryNpc.ITEM_SHOP, 0, 1);
        b.npc(StoryNpc.ITEM_SHOP, 1, 1);
        b.npc(StoryNpc.ITEM_SHOP, 2, 1);
        b.npc(StoryNpc.ITEM_SHOP, 3, 1);
        b.npc(StoryNpc.ITEM_SHOP, 4, 1);
        b.npc(StoryNpc.ITEM_SHOP, 5, 1);
        b.npc(StoryNpc.ITEM_SHOP, 6, 1);
        b.npc(StoryNpc.ITEM_SHOP, 7, 1);
        b.npc(StoryNpc.ITEM_SHOP, 8, 1);
        b.npc(StoryNpc.ITEM_SHOP, 9, 1);

        b.npc(StoryNpc.BLACKSMITH, 0, 1);
        b.npc(StoryNpc.BLACKSMITH, 1, 1);
        b.npc(StoryNpc.BLACKSMITH, 2, 1);
        b.npc(StoryNpc.BLACKSMITH, 3, 1);
        b.npc(StoryNpc.BLACKSMITH, 4, 1);
        b.npc(StoryNpc.BLACKSMITH, 5, 1);
        b.npc(StoryNpc.BLACKSMITH, 6, 1);
        b.npc(StoryNpc.BLACKSMITH, 7, 1);
        b.npc(StoryNpc.BLACKSMITH, 8, 1);
        b.npc(StoryNpc.BLACKSMITH, 9, 1);

        b.npc(StoryNpc.JOB_MASTER, 0, 1);
        b.npc(StoryNpc.JOB_MASTER, 1, 1);
        b.npc(StoryNpc.JOB_MASTER, 2, 1);
        b.npc(StoryNpc.JOB_MASTER, 3, 1);
        b.npc(StoryNpc.JOB_MASTER, 4, 1);
        b.npc(StoryNpc.JOB_MASTER, 5, 1);
        b.npc(StoryNpc.JOB_MASTER, 6, 1);
        b.npc(StoryNpc.JOB_MASTER, 7, 1);
        b.npc(StoryNpc.JOB_MASTER, 8, 1);
        b.npc(StoryNpc.JOB_MASTER, 9, 1);

        b.npc(StoryNpc.AUCTION, 0, 1);
        b.npc(StoryNpc.AUCTION, 1, 1);
        b.npc(StoryNpc.AUCTION, 2, 1);
        b.npc(StoryNpc.AUCTION, 3, 1);
        b.npc(StoryNpc.AUCTION, 4, 1);
        b.npc(StoryNpc.AUCTION, 5, 1);
        b.npc(StoryNpc.AUCTION, 6, 1);
        b.npc(StoryNpc.AUCTION, 7, 1);
        b.npc(StoryNpc.AUCTION, 8, 1);
        b.npc(StoryNpc.AUCTION, 9, 1);

        b.npc(StoryNpc.MAILBOX, 0, 1);
        b.npc(StoryNpc.MAILBOX, 1, 1);
        b.npc(StoryNpc.MAILBOX, 2, 1);
        b.npc(StoryNpc.MAILBOX, 3, 1);
        b.npc(StoryNpc.MAILBOX, 4, 1);
        b.npc(StoryNpc.MAILBOX, 5, 1);
        b.npc(StoryNpc.MAILBOX, 6, 1);
        b.npc(StoryNpc.MAILBOX, 7, 1);
        b.npc(StoryNpc.MAILBOX, 8, 1);
        b.npc(StoryNpc.MAILBOX, 9, 1);

        b.npc(StoryNpc.GUILD_REGISTRAR, 0, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 1, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 2, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 3, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 4, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 5, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 6, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 7, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 8, 1);
        b.npc(StoryNpc.GUILD_REGISTRAR, 9, 1);

        b.npc(StoryNpc.GATEKEEPER, 0, 1);
        b.npc(StoryNpc.GATEKEEPER, 1, 1);
        b.npc(StoryNpc.GATEKEEPER, 2, 1);
        b.npc(StoryNpc.GATEKEEPER, 3, 1);
        b.npc(StoryNpc.GATEKEEPER, 4, 1);
        b.npc(StoryNpc.GATEKEEPER, 5, 1);
        b.npc(StoryNpc.GATEKEEPER, 6, 1);
        b.npc(StoryNpc.GATEKEEPER, 7, 1);
        b.npc(StoryNpc.GATEKEEPER, 8, 1);
        b.npc(StoryNpc.GATEKEEPER, 9, 1);

        b.npc(StoryNpc.ARENA_MASTER, 0, 1);
        b.npc(StoryNpc.ARENA_MASTER, 1, 1);
        b.npc(StoryNpc.ARENA_MASTER, 2, 1);
        b.npc(StoryNpc.ARENA_MASTER, 3, 1);
        b.npc(StoryNpc.ARENA_MASTER, 4, 1);
        b.npc(StoryNpc.ARENA_MASTER, 5, 1);
        b.npc(StoryNpc.ARENA_MASTER, 6, 1);
        b.npc(StoryNpc.ARENA_MASTER, 7, 1);
        b.npc(StoryNpc.ARENA_MASTER, 8, 1);
        b.npc(StoryNpc.ARENA_MASTER, 9, 1);

        b.npc(StoryNpc.HALL_OF_FAME, 0, 2);
        b.npc(StoryNpc.HALL_OF_FAME, 1, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 2, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 3, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 4, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 5, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 6, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 7, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 8, 1);
        b.npc(StoryNpc.HALL_OF_FAME, 9, 2);
    }

    /** Story characters in the capital, from the progress they appear at. */
    private static void storyCharacters(StoryCatalog.Builder b) {
        b.npc(StoryNpc.LILIA, 0, 2);
        b.npc(StoryNpc.LILIA, 1, 2);
        b.npc(StoryNpc.LILIA, 2, 2);
        b.npc(StoryNpc.LILIA, 3, 2);
        b.npc(StoryNpc.LILIA, 4, 2);
        b.npc(StoryNpc.LILIA, 5, 1);
        b.npc(StoryNpc.LILIA, 6, 2);
        b.npc(StoryNpc.LILIA, 7, 2);
        b.npc(StoryNpc.LILIA, 8, 2);
        b.npc(StoryNpc.LILIA, 9, 2);

        b.npc(StoryNpc.GUSTAV, 0, 2);
        b.npc(StoryNpc.GUSTAV, 1, 1);
        b.npc(StoryNpc.GUSTAV, 2, 2);
        b.npc(StoryNpc.GUSTAV, 3, 2);
        b.npc(StoryNpc.GUSTAV, 4, 2);
        b.npc(StoryNpc.GUSTAV, 5, 1);
        b.npc(StoryNpc.GUSTAV, 6, 1);
        b.npc(StoryNpc.GUSTAV, 7, 1);
        b.npc(StoryNpc.GUSTAV, 8, 2);
        b.npc(StoryNpc.GUSTAV, 9, 2);

        b.npc(StoryNpc.NOX, 0, 2);
        b.npc(StoryNpc.NOX, 1, 2);
        b.npc(StoryNpc.NOX, 2, 1);
        b.npc(StoryNpc.NOX, 3, 2);
        b.npc(StoryNpc.NOX, 4, 1);
        b.npc(StoryNpc.NOX, 5, 1);
        b.npc(StoryNpc.NOX, 6, 1);
        b.npc(StoryNpc.NOX, 7, 2);
        b.npc(StoryNpc.NOX, 8, 2);
        b.npc(StoryNpc.NOX, 9, 2);

        b.npc(StoryNpc.LASZLO, 1, 2);
        b.npc(StoryNpc.LASZLO, 2, 1);
        b.npc(StoryNpc.LASZLO, 3, 2);
        b.npc(StoryNpc.LASZLO, 4, 1);
        b.npc(StoryNpc.LASZLO, 5, 1);
        b.npc(StoryNpc.LASZLO, 6, 1);
        b.npc(StoryNpc.LASZLO, 7, 1);
        b.npc(StoryNpc.LASZLO, 8, 1);
        b.npc(StoryNpc.LASZLO, 9, 1);

        b.npc(StoryNpc.WOLFRAM, 4, 2);
        b.npc(StoryNpc.WOLFRAM, 5, 1);
        b.npc(StoryNpc.WOLFRAM, 6, 1);
        b.npc(StoryNpc.WOLFRAM, 7, 1);
        b.npc(StoryNpc.WOLFRAM, 8, 1);
        b.npc(StoryNpc.WOLFRAM, 9, 1);
    }

    /** Quest givers when there is no quest to talk about (latest earlier line applies). */
    private static void questGiverLines(StoryCatalog.Builder b) {
        b.npc(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, 0, 1);
        b.npc(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, 1, 1);
        b.npc(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, 3, 1);
        b.npc(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, 4, 2);
        b.npc(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, 5, 1);
        b.npc(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, 8, 1);
        b.npc(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, 9, 1);

        b.npc(StoryNpc.ELDER_FOREST_SAGE, 1, 1);
        b.npc(StoryNpc.ELDER_FOREST_SAGE, 2, 1);
        b.npc(StoryNpc.ELDER_FOREST_SAGE, 9, 1);

        b.npc(StoryNpc.SANDRIA_CARAVAN_MASTER, 2, 1);
        b.npc(StoryNpc.SANDRIA_CARAVAN_MASTER, 3, 1);
        b.npc(StoryNpc.SANDRIA_CARAVAN_MASTER, 9, 1);

        b.npc(StoryNpc.NORDIA_CHIEFTAIN, 3, 1);
        b.npc(StoryNpc.NORDIA_CHIEFTAIN, 4, 1);
        b.npc(StoryNpc.NORDIA_CHIEFTAIN, 5, 1);
        b.npc(StoryNpc.NORDIA_CHIEFTAIN, 9, 1);

        b.npc(StoryNpc.DRAGONIA_BLACKSMITH, 5, 1);
        b.npc(StoryNpc.DRAGONIA_BLACKSMITH, 6, 1);
        b.npc(StoryNpc.DRAGONIA_BLACKSMITH, 9, 1);

        b.npc(StoryNpc.CELESTIA_ORACLE, 6, 1);
        b.npc(StoryNpc.CELESTIA_ORACLE, 7, 1);
        b.npc(StoryNpc.CELESTIA_ORACLE, 9, 1);

        b.npc(StoryNpc.TOWER_WATCHER, 7, 1);
        b.npc(StoryNpc.TOWER_WATCHER, 8, 1);
        b.npc(StoryNpc.TOWER_WATCHER, 9, 1);
    }

    /** Quest dialogues (quest module ids): keys dialogue.eternalrealm.<npc>.<quest>.<n>. */
    private static void quests(StoryCatalog.Builder b) {
        b.quest(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, "m1_arrival", 4);
        b.quest(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, "m1_hunt", 2);
        b.quest(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, "m1_dungeon", 2);
        b.quest(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, "m1_boss", 2);
        b.quest(StoryNpc.ELDER_FOREST_SAGE, "m2_arrival", 3);
        b.quest(StoryNpc.ELDER_FOREST_SAGE, "m2_hunt", 2);
        b.quest(StoryNpc.ELDER_FOREST_SAGE, "m2_dungeon", 2);
        b.quest(StoryNpc.ELDER_FOREST_SAGE, "m2_boss", 2);
        b.quest(StoryNpc.SANDRIA_CARAVAN_MASTER, "m3_arrival", 4);
        b.quest(StoryNpc.SANDRIA_CARAVAN_MASTER, "m3_hunt", 1);
        b.quest(StoryNpc.SANDRIA_CARAVAN_MASTER, "m3_dungeon", 2);
        b.quest(StoryNpc.SANDRIA_CARAVAN_MASTER, "m3_boss", 2);
        b.quest(StoryNpc.NORDIA_CHIEFTAIN, "m4_arrival", 3);
        b.quest(StoryNpc.NORDIA_CHIEFTAIN, "m4_hunt", 1);
        b.quest(StoryNpc.NORDIA_CHIEFTAIN, "m4_dungeon1", 2);
        b.quest(StoryNpc.NORDIA_CHIEFTAIN, "m4_boss1", 2);
        b.quest(StoryNpc.NORDIA_CHIEFTAIN, "m4_dungeon2", 1);
        b.quest(StoryNpc.NORDIA_CHIEFTAIN, "m4_boss2", 2);
        b.quest(StoryNpc.DRAGONIA_BLACKSMITH, "m5_arrival", 4);
        b.quest(StoryNpc.DRAGONIA_BLACKSMITH, "m5_hunt", 1);
        b.quest(StoryNpc.DRAGONIA_BLACKSMITH, "m5_dungeon", 1);
        b.quest(StoryNpc.DRAGONIA_BLACKSMITH, "m5_boss", 2);
        b.quest(StoryNpc.CELESTIA_ORACLE, "m6_arrival", 4);
        b.quest(StoryNpc.CELESTIA_ORACLE, "m6_hunt", 1);
        b.quest(StoryNpc.CELESTIA_ORACLE, "m6_dungeon", 2);
        b.quest(StoryNpc.CELESTIA_ORACLE, "m6_boss", 2);
        b.quest(StoryNpc.TOWER_WATCHER, "m7_arrival", 3);
        b.quest(StoryNpc.TOWER_WATCHER, "m7_hunt", 2);
        b.quest(StoryNpc.TOWER_WATCHER, "m7_dungeon", 1);
        b.quest(StoryNpc.TOWER_WATCHER, "m7_boss", 2);
        b.quest(StoryNpc.TOWER_WATCHER, "m7_final", 2);
        b.quest(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, "s1_slime_gel", 2);
        b.quest(StoryNpc.ELDER_FOREST_SAGE, "s2_forest_spores", 1);
        b.quest(StoryNpc.SANDRIA_CARAVAN_MASTER, "s3_tomb_warden", 1);
        b.quest(StoryNpc.NORDIA_CHIEFTAIN, "s4_yeti_hunt", 1);
        b.quest(StoryNpc.DRAGONIA_BLACKSMITH, "s5_drake_scales", 1);
        b.quest(StoryNpc.CELESTIA_ORACLE, "s6_star_fragments", 1);
        b.quest(StoryNpc.ARCELIA_KNIGHT_CAPTAIN, "daily_plains_patrol", 1);
        b.quest(StoryNpc.SANDRIA_CARAVAN_MASTER, "daily_desert_supplies", 1);
        b.quest(StoryNpc.DRAGONIA_BLACKSMITH, "daily_volcano_expedition", 1);
        b.quest(StoryNpc.TOWER_WATCHER, "daily_tower_vigil", 1);
    }
}
