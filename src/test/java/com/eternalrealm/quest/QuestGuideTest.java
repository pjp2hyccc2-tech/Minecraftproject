package com.eternalrealm.quest;

import com.eternalrealm.world.field.FieldLayout;
import com.eternalrealm.world.field.Habitats;
import com.eternalrealm.world.gen.LandmarkSites;
import com.eternalrealm.world.guide.AstoriaPlaces;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QuestGuideTest {
    private static final AstoriaPlaces PLACES = new AstoriaPlaces(id -> Optional.empty());
    private static final QuestGuide.Point CAPITAL = AstoriaPlaces.capital();

    private static QuestDefinition quest(String id) {
        return Quests.catalog().byId(id).orElseThrow();
    }

    @Test
    public void anArrivalLeadsIntoTheFieldThenToItsGuide() {
        QuestDefinition arrival = quest("m2_arrival");
        QuestGuide.Target first = QuestGuide.target(arrival, List.of(0, 0), false, CAPITAL, PLACES).orElseThrow();
        assertEquals(QuestGuide.Kind.AREA, first.kind());
        assertEquals(0, first.objective());
        assertEquals(FieldLayout.MOON_FOREST, FieldLayout.areaAt(first.point().x(), first.point().z()));
        assertTrue("just inside, not the far middle", first.point().distanceTo(CAPITAL.x(), CAPITAL.z())
                < Math.hypot(FieldLayout.labelPoint(2)[0] - CAPITAL.x(), FieldLayout.labelPoint(2)[1] - CAPITAL.z())
                + 1.0);

        QuestGuide.Target second = QuestGuide.target(arrival, List.of(1, 0), false, CAPITAL, PLACES).orElseThrow();
        assertEquals(QuestGuide.Kind.TALK, second.kind());
        assertEquals("elder_forest_sage", second.subject());
        var gate = LandmarkSites.byId("d2").orElseThrow();
        assertTrue("the sage waits by the forest dungeon's gate", second.point().distanceTo(gate.x(), gate.z()) < 2.0);
    }

    @Test
    public void huntsLeadToTheNearestHauntOfTheMonster() {
        QuestDefinition hunt = quest("m2_hunt");
        QuestGuide.Point here = new QuestGuide.Point(-900, -100);
        QuestGuide.Target target = QuestGuide.target(hunt, List.of(0, 0), false, here, PLACES).orElseThrow();
        assertEquals(QuestGuide.Kind.HUNT, target.kind());
        assertEquals("forest_spider", target.subject());
        double best = Double.MAX_VALUE;
        for (Habitats.Habitat habitat : Habitats.of("forest_spider")) {
            best = Math.min(best, Math.hypot(habitat.x() + 0.5 - here.x(), habitat.z() + 0.5 - here.z()));
        }
        assertEquals(best, target.point().distanceTo(here.x(), here.z()), 1e-6);
        QuestGuide.Target next = QuestGuide.target(hunt, List.of(8, 0), false, here, PLACES).orElseThrow();
        assertEquals("a finished objective is skipped", "forest_witch", next.subject());
    }

    @Test
    public void dungeonsAndBossesLeadToTheEntranceAndTurningInToTheGiver() {
        QuestGuide.Target dungeon = QuestGuide.target(quest("m2_dungeon"), List.of(0), false, CAPITAL, PLACES)
                .orElseThrow();
        assertEquals(QuestGuide.Kind.DUNGEON, dungeon.kind());
        var gate = LandmarkSites.byId("d2").orElseThrow();
        assertEquals(gate.x() + 0.5, dungeon.point().x(), 1e-9);
        QuestGuide.Target boss = QuestGuide.target(quest("m2_boss"), List.of(0), false, CAPITAL, PLACES).orElseThrow();
        assertEquals(QuestGuide.Kind.BOSS, boss.kind());
        assertEquals(dungeon.point(), boss.point());
        QuestGuide.Target last = QuestGuide.target(quest("m7_final"), List.of(0), false, CAPITAL, PLACES).orElseThrow();
        var tower = LandmarkSites.byId("d8").orElseThrow();
        assertEquals("the final boss is fought from the tower", tower.x() + 0.5, last.point().x(), 1e-9);

        QuestGuide.Target turnIn = QuestGuide.target(quest("m2_dungeon"), List.of(1), true, CAPITAL, PLACES)
                .orElseThrow();
        assertEquals(QuestGuide.Kind.TURN_IN, turnIn.kind());
        assertEquals("elder_forest_sage", turnIn.subject());
        assertEquals(-1, turnIn.objective());
    }

    @Test
    public void collectingLeadsToAMonsterThatDropsIt() {
        QuestDefinition gel = quest("s1_slime_gel");
        QuestGuide.Target target = QuestGuide.target(gel, List.of(0), false, CAPITAL, PLACES).orElseThrow();
        assertEquals(QuestGuide.Kind.COLLECT, target.kind());
        Objective.CollectItems collect = (Objective.CollectItems) gel.objectives().get(0);
        assertEquals(collect.itemId(), target.subject());
        assertFalse(PLACES.droppers(collect.itemId()).isEmpty());
    }

    @Test
    public void whereTheServerSawAnNpcWins() {
        AstoriaPlaces seen = new AstoriaPlaces(id -> id.equals("arcelia_knight_captain")
                ? Optional.of(new QuestGuide.Point(12, 34)) : Optional.empty());
        assertEquals(new QuestGuide.Point(12, 34), seen.npc("arcelia_knight_captain").orElseThrow());
        assertEquals("unseen, the knight captain is in the capital", CAPITAL,
                PLACES.npc("arcelia_knight_captain").orElseThrow());
        assertTrue(PLACES.npc("nobody").isEmpty());
    }

    @Test
    public void inTheFieldAlreadyTheEntryIsWhereYouStand() {
        double[] middle = FieldLayout.labelPoint(FieldLayout.MOON_FOREST);
        QuestGuide.Point inside = new QuestGuide.Point(middle[0], middle[1]);
        assertEquals(inside, PLACES.areaEntry(FieldLayout.MOON_FOREST, inside).orElseThrow());
        assertTrue(PLACES.areaEntry(99, inside).isEmpty());
    }

    @Test
    public void theMainQuestIsGuidedUnlessThePlayerChoseAnother() {
        QuestCatalog catalog = Quests.catalog();
        assertEquals("m2_hunt", QuestGuide.guided(List.of("s1_slime_gel", "m2_hunt"), null, catalog).orElseThrow());
        assertEquals("s1_slime_gel", QuestGuide.guided(List.of("s1_slime_gel", "m2_hunt"), "s1_slime_gel", catalog)
                .orElseThrow());
        assertEquals("a finished choice falls back", "m2_hunt",
                QuestGuide.guided(List.of("m2_hunt"), "s1_slime_gel", catalog).orElseThrow());
        assertTrue(QuestGuide.guided(List.of(), null, catalog).isEmpty());
    }
}
