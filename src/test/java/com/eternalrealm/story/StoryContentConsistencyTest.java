package com.eternalrealm.story;

import com.eternalrealm.boss.BossDefinition;
import com.eternalrealm.boss.Bosses;
import com.eternalrealm.dungeon.DungeonDefinition;
import com.eternalrealm.dungeon.Dungeons;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The story must follow the game's actual content: bosses and their phases, dungeons, areas and town NPC roles. */
public class StoryContentConsistencyTest {
    private static final StoryCatalog CATALOG = StoryCatalog.standard();
    private static final Path NPC_ROLES = Paths.get("src", "main", "java", "com", "eternalrealm", "npc", "forge",
            "NpcRole.java");

    @Test
    public void mainBossesAreTheBossesOfTheGame() {
        List<String> ids = new ArrayList<>();
        for (BossDefinition boss : Bosses.all()) {
            ids.add(boss.id());
        }
        assertEquals(ids, StoryProgress.MAIN_BOSSES);
    }

    @Test
    public void chaptersFollowTheDungeons() {
        List<String> chapterDungeons = new ArrayList<>();
        for (Chapter chapter : Chapter.values()) {
            chapter.dungeonId().ifPresent(chapterDungeons::add);
            if (chapter.dungeonId().isEmpty()) {
                continue;
            }
            String dungeonId = chapter.dungeonId().get();
            if (dungeonId.equals(Dungeons.FINAL_RAID.id())) {
                assertEquals(Dungeons.FINAL_RAID.areaId(), chapter.area().id());
                assertEquals(Dungeons.FINAL_RAID.bossId(), chapter.bossId().orElseThrow());
            } else {
                DungeonDefinition dungeon = Dungeons.byId(dungeonId).orElseThrow();
                assertEquals(chapter + " area", dungeon.areaId(), chapter.area().id());
                assertEquals(chapter + " boss", dungeon.bossId(), chapter.bossId().orElseThrow());
            }
        }
        List<String> expected = new ArrayList<>();
        for (DungeonDefinition dungeon : Dungeons.all()) {
            expected.add(dungeon.id());
        }
        expected.add(Dungeons.FINAL_RAID.id());
        assertEquals("one chapter per dungeon, in order, then the final battle", expected, chapterDungeons);
        for (StoryArea area : StoryArea.values()) {
            assertTrue(area + " has a dungeon", Dungeons.inArea(area.id()).size() > 0
                    || area.id() == Dungeons.FINAL_RAID.areaId());
        }
    }

    @Test
    public void everyDungeonAndBossHasItsScenes() {
        for (DungeonDefinition dungeon : Dungeons.all()) {
            assertTrue(dungeon.id(), CATALOG.dialogue(new StoryTrigger.DungeonEntered(dungeon.id())).isPresent());
        }
        assertTrue(CATALOG.dialogue(new StoryTrigger.DungeonEntered(Dungeons.FINAL_RAID.id())).isPresent());
        for (BossDefinition boss : Bosses.all()) {
            assertTrue(boss.id(), CATALOG.dialogue(new StoryTrigger.BossEncounter(boss.id())).isPresent());
            assertTrue(boss.id(), CATALOG.dialogue(new StoryTrigger.BossDefeated(boss.id(), true)).isPresent());
            assertTrue(boss.id(), CATALOG.dialogue(new StoryTrigger.BossDefeated(boss.id(), false)).isPresent());
        }
    }

    @Test
    public void barksMatchTheBossPhases() {
        for (BossDefinition boss : Bosses.all()) {
            int phases = boss.phases().size();
            boolean finalBoss = boss.id().equals("final");
            assertTrue(CATALOG.bossPhaseBark(boss.id(), 1).isEmpty());
            for (int phase = 2; phase <= phases; phase++) {
                assertEquals(boss.id() + " phase " + phase, !finalBoss,
                        CATALOG.bossPhaseBark(boss.id(), phase).isPresent());
            }
            assertTrue(boss.id(), CATALOG.bossPhaseBark(boss.id(), phases + 1).isEmpty());
            if (finalBoss) {
                assertEquals("one FinalPhase dialogue per phase", 4, phases);
            }
        }
    }

    @Test
    public void townNpcsAreRolesOfTheNpcModule() throws IOException {
        String source = Files.readString(NPC_ROLES, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("[A-Z_]+\\(\"([a-z_]+)\",").matcher(source);
        Set<String> roles = new TreeSet<>();
        while (matcher.find()) {
            roles.add(matcher.group(1));
        }
        assertTrue("story characters are NpcRole.STORY NPCs", roles.contains("story"));
        Set<String> service = new TreeSet<>();
        for (StoryNpc npc : StoryNpc.values()) {
            if (npc.kind() == StoryNpc.Kind.SERVICE) {
                service.add(npc.id());
                assertTrue(npc.id() + " is not an NpcRole", roles.contains(npc.id()));
            } else {
                assertTrue(npc.id() + " must not clash with a role id", !roles.contains(npc.id()));
            }
        }
        assertEquals(Set.of("quest_board", "item_shop", "blacksmith", "job_master", "auction", "mailbox",
                "guild_registrar", "gatekeeper", "arena_master", "hall_of_fame"), service);
    }
}
