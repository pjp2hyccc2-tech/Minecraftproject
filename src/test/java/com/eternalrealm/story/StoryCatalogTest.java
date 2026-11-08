package com.eternalrealm.story;

import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StoryCatalogTest {
    private static final StoryCatalog CATALOG = StoryCatalog.standard();
    private static final List<String> BOSSES = StoryProgress.MAIN_BOSSES;
    private static final List<String> DUNGEONS = List.of("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "final");

    /** The quest module's quests and who gives them (coordination note of the world/quest module). */
    private static final Map<String, List<String>> QUESTS = new LinkedHashMap<>();

    static {
        QUESTS.put("arcelia_knight_captain",
                List.of("m1_arrival", "m1_hunt", "m1_dungeon", "m1_boss", "s1_slime_gel", "daily_plains_patrol"));
        QUESTS.put("elder_forest_sage", List.of("m2_arrival", "m2_hunt", "m2_dungeon", "m2_boss", "s2_forest_spores"));
        QUESTS.put("sandria_caravan_master",
                List.of("m3_arrival", "m3_hunt", "m3_dungeon", "m3_boss", "s3_tomb_warden", "daily_desert_supplies"));
        QUESTS.put("nordia_chieftain", List.of("m4_arrival", "m4_hunt", "m4_dungeon1", "m4_boss1", "m4_dungeon2",
                "m4_boss2", "s4_yeti_hunt"));
        QUESTS.put("dragonia_blacksmith", List.of("m5_arrival", "m5_hunt", "m5_dungeon", "m5_boss", "s5_drake_scales",
                "daily_volcano_expedition"));
        QUESTS.put("celestia_oracle", List.of("m6_arrival", "m6_hunt", "m6_dungeon", "m6_boss", "s6_star_fragments"));
        QUESTS.put("tower_watcher",
                List.of("m7_arrival", "m7_hunt", "m7_dungeon", "m7_boss", "m7_final", "daily_tower_vigil"));
    }

    private static Dialogue play(StoryTrigger trigger) {
        Optional<Dialogue> dialogue = CATALOG.dialogue(trigger);
        assertTrue("no dialogue for " + trigger, dialogue.isPresent());
        return dialogue.get();
    }

    /** Every trigger a season goes through from the first login to the farewell. */
    private static List<StoryTrigger> mainPath() {
        List<StoryTrigger> path = new ArrayList<>();
        path.add(StoryTrigger.FIRST_LOGIN);
        for (StoryArea area : StoryArea.values()) {
            path.add(new StoryTrigger.AreaEntered(area.id()));
            if (area.unlockedBy().isPresent()) {
                path.add(new StoryTrigger.AreaUnlocked(area.id()));
            }
        }
        for (String dungeon : DUNGEONS) {
            path.add(new StoryTrigger.DungeonEntered(dungeon));
        }
        for (String boss : BOSSES) {
            path.add(new StoryTrigger.BossEncounter(boss));
            path.add(new StoryTrigger.BossDefeated(boss, true));
            path.add(new StoryTrigger.BossDefeated(boss, false));
        }
        for (int phase = 1; phase <= 4; phase++) {
            path.add(new StoryTrigger.FinalPhase(phase));
        }
        path.add(StoryTrigger.ENDING);
        for (int day = 1; day <= StoryCatalog.STANDARD_SEASON_DAYS; day++) {
            path.add(new StoryTrigger.DayStarted(day));
            path.add(new StoryTrigger.DayStarted(day, StoryCatalog.STANDARD_SEASON_DAYS, StoryProgress.MAX));
        }
        path.add(new StoryTrigger.SeasonEnded(true));
        path.add(new StoryTrigger.SeasonEnded(false));
        for (StoryNpc npc : StoryNpc.values()) {
            for (int progress = 0; progress <= StoryProgress.MAX; progress++) {
                if (npc.isPresentAt(progress)) {
                    path.add(new StoryTrigger.NpcTalk(npc.id(), progress));
                }
            }
        }
        for (Map.Entry<String, List<String>> giver : QUESTS.entrySet()) {
            for (String quest : giver.getValue()) {
                path.add(new StoryTrigger.QuestTalk(giver.getKey(), quest));
            }
        }
        return path;
    }

    @Test
    public void everyMainPathTriggerHasADialogue() {
        List<String> missing = new ArrayList<>();
        Set<StoryTrigger.Kind> kinds = EnumSet.noneOf(StoryTrigger.Kind.class);
        for (StoryTrigger trigger : mainPath()) {
            kinds.add(trigger.kind());
            if (CATALOG.dialogue(trigger).isEmpty()) {
                missing.add(trigger.toString());
            }
        }
        if (!missing.isEmpty()) {
            fail("no dialogue for:\n  " + String.join("\n  ", missing));
        }
        assertEquals("the main path covers every kind of trigger", EnumSet.allOf(StoryTrigger.Kind.class), kinds);
        for (StoryArea area : StoryArea.values()) {
            assertTrue(area.toString(), CATALOG.areaCard(area.id()).isPresent());
        }
    }

    @Test
    public void triggersSelectTheMatchingDialogue() {
        assertEquals("prologue", play(StoryTrigger.FIRST_LOGIN).id());
        assertEquals("area.sandria_desert.enter", play(new StoryTrigger.AreaEntered(3)).id());
        assertEquals("area.dragonia_volcano.unlocked", play(new StoryTrigger.AreaUnlocked(5)).id());
        assertEquals("dungeon.final.enter", play(new StoryTrigger.DungeonEntered("final")).id());
        assertEquals("boss.b4.intro", play(new StoryTrigger.BossEncounter("b4")).id());
        assertEquals("boss.b4.first_defeat", play(new StoryTrigger.BossDefeated("b4", true)).id());
        assertEquals("boss.b4.defeat", play(new StoryTrigger.BossDefeated("b4", false)).id());
        assertEquals("final.phase3", play(new StoryTrigger.FinalPhase(3)).id());
        assertEquals("quest.nordia_chieftain.m4_boss2", play(new StoryTrigger.QuestTalk("nordia_chieftain", "m4_boss2")).id());
        assertEquals("epilogue.victory", play(new StoryTrigger.SeasonEnded(true)).id());
        assertEquals("epilogue.unfinished", play(new StoryTrigger.SeasonEnded(false)).id());
        assertEquals(CATALOG.ending().asDialogue(), play(StoryTrigger.ENDING));
        assertEquals(play(StoryTrigger.FIRST_LOGIN), CATALOG.byId("prologue").orElseThrow());
    }

    @Test
    public void unknownIdsAndTheStartingAreaHaveNoDialogue() {
        assertTrue(CATALOG.dialogue(new StoryTrigger.AreaUnlocked(1)).isEmpty());
        assertTrue(CATALOG.dialogue(new StoryTrigger.AreaEntered(8)).isEmpty());
        assertTrue(CATALOG.dialogue(new StoryTrigger.DungeonEntered("d9")).isEmpty());
        assertTrue(CATALOG.dialogue(new StoryTrigger.BossEncounter("ex_b1")).isEmpty());
        assertTrue(CATALOG.dialogue(new StoryTrigger.NpcTalk("nobody", 3)).isEmpty());
        assertTrue(CATALOG.dialogue(new StoryTrigger.QuestTalk("tower_watcher", "m1_arrival")).isEmpty());
        assertTrue(CATALOG.areaCard(0).isEmpty());
        assertTrue(CATALOG.bossPhaseBark("b1", 3).isEmpty());
        assertTrue(CATALOG.bossPhaseBark("final", 2).isEmpty());
    }

    @Test
    public void dialogueIdsAndLineKeysAreUnique() {
        Set<String> ids = new HashSet<>();
        Map<String, String> owner = new HashMap<>();
        for (Dialogue dialogue : CATALOG.dialogues()) {
            assertTrue("duplicate dialogue id " + dialogue.id(), ids.add(dialogue.id()));
            for (DialogueLine line : dialogue.lines()) {
                String previous = owner.put(line.textKey(), dialogue.id());
                assertTrue(line.textKey() + " used by " + previous + " and " + dialogue.id(), previous == null);
            }
        }
        for (String boss : BOSSES) {
            for (int phase = 2; phase <= 4; phase++) {
                CATALOG.bossPhaseBark(boss, phase).ifPresent(bark ->
                        assertTrue(bark.textKey(), owner.put(bark.textKey(), "bark") == null));
            }
        }
        for (EndingSequence.Credit credit : CATALOG.ending().staffRoll()) {
            assertTrue(credit.textKey(), owner.put(credit.textKey(), "credit") == null);
        }
    }

    @Test
    public void everySpeakerSpeaks() {
        Set<String> speaking = new HashSet<>();
        for (Dialogue dialogue : CATALOG.dialogues()) {
            for (DialogueLine line : dialogue.lines()) {
                speaking.add(line.speakerKey());
            }
        }
        for (Speaker speaker : Speaker.values()) {
            assertTrue(speaker + " never speaks", speaking.contains(speaker.key()));
        }
    }

    @Test
    public void scenesStayShort() {
        for (Dialogue dialogue : CATALOG.dialogues()) {
            int max = dialogue.id().startsWith("npc.") || dialogue.id().startsWith("quest.") ? 4
                    : dialogue.id().equals("ending") ? 30 : 14;
            assertTrue(dialogue.id() + " has " + dialogue.size() + " lines", dialogue.size() <= max);
        }
        assertEquals(14 * StoryCatalog.LINE_INTERVAL_TICKS, play(StoryTrigger.FIRST_LOGIN).durationTicks());
    }

    @Test
    public void townNpcsSayDifferentThingsAsTheServerProgresses() {
        for (StoryNpc npc : StoryNpc.values()) {
            if (npc.kind() == StoryNpc.Kind.QUEST_GIVER) {
                continue;
            }
            Dialogue previous = null;
            for (int progress = npc.firstProgress(); progress <= StoryProgress.MAX; progress++) {
                Dialogue dialogue = play(new StoryTrigger.NpcTalk(npc.id(), progress));
                assertEquals("npc." + npc.id() + "." + progress, dialogue.id());
                for (DialogueLine line : dialogue.lines()) {
                    assertEquals(dialogue.id(), npc.speaker().key(), line.speakerKey());
                }
                assertNotEquals(npc + " at " + progress, previous, dialogue);
                previous = dialogue;
            }
            assertEquals(previous, play(new StoryTrigger.NpcTalk(npc.id(), 99)));
        }
    }

    @Test
    public void storyCharactersAppearWithTheirChapter() {
        assertTrue(CATALOG.dialogue(new StoryTrigger.NpcTalk("laszlo", 0)).isEmpty());
        assertEquals("npc.laszlo.1", play(new StoryTrigger.NpcTalk("laszlo", 1)).id());
        assertTrue(CATALOG.dialogue(new StoryTrigger.NpcTalk("wolfram", 3)).isEmpty());
        assertEquals("npc.wolfram.4", play(new StoryTrigger.NpcTalk("wolfram", 4)).id());
        assertFalse(StoryNpc.WOLFRAM.isPresentAt(3));
        assertTrue(StoryNpc.WOLFRAM.isPresentAt(4));
        assertTrue("quest givers and service NPCs are always there", StoryNpc.TOWER_WATCHER.isPresentAt(0)
                && StoryNpc.ITEM_SHOP.isPresentAt(0));
    }

    @Test
    public void questGiversUseTheirLatestLineOrTheirFirst() {
        assertEquals("npc.arcelia_knight_captain.1", play(new StoryTrigger.NpcTalk("arcelia_knight_captain", 2)).id());
        assertEquals("npc.arcelia_knight_captain.4", play(new StoryTrigger.NpcTalk("arcelia_knight_captain", 4)).id());
        assertEquals("npc.elder_forest_sage.2", play(new StoryTrigger.NpcTalk("elder_forest_sage", 8)).id());
        assertEquals("npc.elder_forest_sage.9", play(new StoryTrigger.NpcTalk("elder_forest_sage", 9)).id());
        assertEquals("before the area opens, the first line", "npc.tower_watcher.7",
                play(new StoryTrigger.NpcTalk("tower_watcher", 0)).id());
        for (StoryNpc npc : StoryNpc.values()) {
            if (npc.kind() == StoryNpc.Kind.QUEST_GIVER) {
                assertEquals(npc.toString(), "npc." + npc.id() + "." + npc.firstProgress(),
                        play(new StoryTrigger.NpcTalk(npc.id(), npc.firstProgress())).id());
                assertEquals(npc.toString(), "npc." + npc.id() + ".9", play(new StoryTrigger.NpcTalk(npc.id(), 9)).id());
            }
        }
    }

    @Test
    public void everyQuestHasADialogueWithItsGiver() {
        for (Map.Entry<String, List<String>> giver : QUESTS.entrySet()) {
            StoryNpc npc = StoryNpc.byId(giver.getKey()).orElseThrow();
            assertEquals(StoryNpc.Kind.QUEST_GIVER, npc.kind());
            assertEquals(giver.getValue(), CATALOG.questIds(npc));
            for (String quest : giver.getValue()) {
                Dialogue dialogue = play(new StoryTrigger.QuestTalk(npc.id(), quest));
                assertEquals(StoryCatalog.QUEST_KEY_PREFIX + npc.id() + "." + quest + ".1", dialogue.first().textKey());
                for (DialogueLine line : dialogue.lines()) {
                    assertEquals(npc.speaker().key(), line.speakerKey());
                }
            }
        }
        int questGivers = 0;
        for (StoryNpc npc : StoryNpc.values()) {
            if (npc.kind() == StoryNpc.Kind.QUEST_GIVER) {
                questGivers++;
                assertTrue(npc.id(), QUESTS.containsKey(npc.id()));
            } else {
                assertTrue(npc.id() + " gives no quests", CATALOG.questIds(npc).isEmpty());
            }
        }
        assertEquals(QUESTS.size(), questGivers);
    }

    @Test
    public void daysFollowTheSeasonWhateverItsLength() {
        for (int day = 1; day <= 12; day++) {
            assertEquals("day." + day, play(new StoryTrigger.DayStarted(day)).id());
        }
        assertEquals("day.eve", play(new StoryTrigger.DayStarted(13)).id());
        assertEquals("day.final", play(new StoryTrigger.DayStarted(14)).id());
        assertEquals("day.final", play(new StoryTrigger.DayStarted(15)).id());
        assertEquals("day.3", play(new StoryTrigger.DayStarted(3, 7, 0)).id());
        assertEquals("day.eve", play(new StoryTrigger.DayStarted(6, 7, 4)).id());
        assertEquals("day.final", play(new StoryTrigger.DayStarted(7, 7, 8)).id());
        assertEquals("day.12", play(new StoryTrigger.DayStarted(12, 21, 0)).id());
        assertEquals("day.extra", play(new StoryTrigger.DayStarted(13, 21, 0)).id());
        assertEquals("day.extra", play(new StoryTrigger.DayStarted(19, 21, 0)).id());
        assertEquals("day.eve", play(new StoryTrigger.DayStarted(20, 21, 0)).id());
        assertEquals("day.final", play(new StoryTrigger.DayStarted(21, 21, 0)).id());
        assertEquals("day.final", play(new StoryTrigger.DayStarted(1, 1, 0)).id());
        assertEquals("after the victory", "day.peace", play(new StoryTrigger.DayStarted(13, 14, 9)).id());
        assertEquals("day.farewell", play(new StoryTrigger.DayStarted(14, 14, 9)).id());
        assertEquals("day.farewell", play(new StoryTrigger.DayStarted(14, 14, 12)).id());
    }

    @Test
    public void endingLastsAboutAMinuteThenRollsTheCredits() {
        EndingSequence ending = CATALOG.ending();
        List<EndingSequence.Beat> beats = ending.beats();
        assertEquals(0, beats.get(0).atTick());
        for (int i = 1; i < beats.size(); i++) {
            assertTrue("at least 2 s between lines: " + i,
                    beats.get(i).atTick() - beats.get(i - 1).atTick() >= StoryCatalog.LINE_INTERVAL_TICKS);
        }
        int last = beats.get(beats.size() - 1).atTick();
        assertTrue("about a minute: " + last, last >= 50 * 20 && last <= 70 * 20);
        assertEquals("the last line is the big thank-you", EndingSequence.Display.TITLE,
                beats.get(beats.size() - 1).display());
        assertTrue(beats.stream().anyMatch(beat -> beat.line().speakerKey().equals(Speaker.KANATA.key())));
        assertEquals(EndingSequence.CreditKind.HEADING, ending.staffRoll().get(0).kind());
        assertEquals(1, ending.staffRoll().stream().filter(c -> c.kind() == EndingSequence.CreditKind.PLAYERS).count());
        assertEquals(last + EndingSequence.HOLD_TICKS, ending.staffRollStartTick());
        assertTrue("ending and credits stay under three minutes", ending.totalTicks() <= 3 * 60 * 20);
    }

    @Test
    public void firstKillsRevealAStarMemoryAndLaterKillsAreShort() {
        Set<String> firstParty = Set.of(Speaker.KANATA.key(), Speaker.ELMINA.key(), Speaker.GLAUS.key(),
                Speaker.YOUNG_MAGE.key());
        for (String boss : BOSSES.subList(0, 8)) {
            Dialogue first = play(new StoryTrigger.BossDefeated(boss, true));
            Dialogue later = play(new StoryTrigger.BossDefeated(boss, false));
            assertTrue(boss + ": the first kill shows a memory of the first hero's journey",
                    first.lines().stream().anyMatch(line -> firstParty.contains(line.speakerKey())));
            assertTrue(boss, first.lines().stream().anyMatch(DialogueLine::isNarration));
            assertTrue(boss, later.size() <= 2 && first.size() > later.size());
        }
        assertEquals("the King's true voice breaks through in the last phase", Speaker.KANATA.key(),
                play(new StoryTrigger.FinalPhase(4)).lines().get(1).speakerKey());
    }

    @Test
    public void triggerAudiences() {
        assertEquals(StoryTrigger.Audience.PLAYER, StoryTrigger.FIRST_LOGIN.audience());
        assertEquals(StoryTrigger.Audience.SERVER, new StoryTrigger.BossDefeated("b1", true).audience());
        assertEquals(StoryTrigger.Audience.PARTY, new StoryTrigger.BossDefeated("b1", false).audience());
        assertEquals(StoryTrigger.Audience.SERVER, StoryTrigger.ENDING.audience());
    }
}
