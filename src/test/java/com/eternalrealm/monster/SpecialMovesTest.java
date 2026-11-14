package com.eternalrealm.monster;

import com.eternalrealm.combat.Telegraph;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SpecialMovesTest {
    /** The variant ids of the four enemy rigs (entity.RigData, which this pure test cannot load). */
    private static final Map<String, List<String>> RIGS = Map.of(
            "humanoid", List.of("bandit", "ghoul", "mummy", "cultist", "prisoner", "jailer", "ice_knight", "frost_mage",
                    "fire_imp", "dark_knight", "void_mage", "shade", "crossbowman", "dryad"),
            "beast", List.of("wolf", "snow_wolf", "cave_rat", "frost_hound", "abyss_hound", "salamander", "scorpion"),
            "golem", List.of("treant", "sand_golem", "ice_golem", "lava_golem", "ruin_construct"),
            "flyer", List.of("harpy", "ice_wraith", "storm_spirit", "sky_sentinel", "fire_spirit"));

    @Test
    public void elitesAndVeteransGetMovesOrdinaryMonstersDoNot() {
        assertEquals(2, SpecialMoves.moveCount(MonsterRole.ELITE, 1));
        assertEquals(2, SpecialMoves.moveCount(MonsterRole.ELITE, 50));
        assertEquals(0, SpecialMoves.moveCount(MonsterRole.NORMAL, SpecialMoves.VETERAN_LEVEL - 1));
        assertEquals(1, SpecialMoves.moveCount(MonsterRole.NORMAL, SpecialMoves.VETERAN_LEVEL));
        assertEquals("boss summons never", 0, SpecialMoves.moveCount(MonsterRole.MINION, 50));
        assertTrue(SpecialMoves.cooldownTicks(2) < SpecialMoves.cooldownTicks(1));
        assertTrue("the lords move most often", SpecialMoves.cooldownTicks(SpecialMoves.LORD_MOVES)
                < SpecialMoves.cooldownTicks(2));
    }

    @Test
    public void everyBestiaryBodyHasThreeDistinctMovesForItsLordsAndVeterans() {
        int bodies = 0;
        for (MonsterTemplate template : Monsters.all()) {
            String id = template.id();
            if (!id.matches("(f\\d+|lord)_[a-z_]+")) {
                continue;
            }
            bodies++;
            List<SpecialMove> moves = BestiaryMoves.movesOf(id);
            assertTrue(id, moves != null && moves.size() == 3 && new java.util.HashSet<>(moves).size() == 3);
            assertEquals(moves, SpecialMoves.forBody("any", id));
            assertEquals(moves.subList(0, 1), SpecialMoves.movesOf("any", id, 1));
            assertEquals(moves, SpecialMoves.movesOf("any", id, SpecialMoves.BODY_MOVES));
            List<SpecialMove> lord = SpecialMoves.movesOf("any", id, SpecialMoves.LORD_MOVES);
            assertEquals(id + ": a lord adds three arts", 6, lord.size());
            assertEquals(moves, lord.subList(0, 3));
            assertEquals(6, new java.util.HashSet<>(lord).size());
            assertTrue(id, BestiaryMoves.styleOf(id) != null);
        }
        assertEquals(130, bodies);
        assertEquals("a fire imp of the ruins burns", Telegraph.Style.FIRE, SpecialMoves.style("f5_ember_imp"));
        assertEquals("the knight of the peaks is holy", Telegraph.Style.HOLY, SpecialMoves.style("lord_alvin"));
        assertEquals(SpecialMove.VOLLEY, SpecialMoves.forBody("humanoid", "lord_peak_sniper").get(0));
        assertEquals("the first bodies keep two moves", 2, SpecialMoves.movesOf("humanoid", "bandit", 3).size());
    }

    @Test
    public void everyBodyHasTwoDistinctMoves() {
        for (Map.Entry<String, List<String>> rig : RIGS.entrySet()) {
            for (String variant : rig.getValue()) {
                List<SpecialMove> moves = SpecialMoves.forBody(rig.getKey(), variant);
                assertEquals(variant, 2, moves.size());
                assertFalse(variant, moves.get(0) == moves.get(1));
                assertEquals(List.of(moves.get(0)), SpecialMoves.movesOf(rig.getKey(), variant, 1));
                assertEquals(moves, SpecialMoves.movesOf(rig.getKey(), variant, 2));
                assertTrue(SpecialMoves.movesOf(rig.getKey(), variant, 0).isEmpty());
            }
        }
        assertTrue(SpecialMoves.forBody("dragon", "b6").isEmpty());
        assertEquals(SpecialMove.CLEAVE, SpecialMoves.forBody("humanoid", "bandit").get(0));
        assertEquals(SpecialMove.CIRCLE, SpecialMoves.forBody("humanoid", "void_mage").get(0));
        assertEquals(SpecialMove.SLAM, SpecialMoves.forBody("golem", "treant").get(0));
        assertEquals(SpecialMove.STOMP, SpecialMoves.forBody("golem", "treant").get(1));
        assertEquals(SpecialMove.BREATH, SpecialMoves.forBody("beast", "salamander").get(0));
        assertEquals(SpecialMove.CHARGE, SpecialMoves.forBody("beast", "abyss_hound").get(0));
    }

    @Test
    public void vanillaBodiesGetMovesTooExceptCreepers() {
        for (String body : MonsterTemplate.BASE_ENTITY_TYPES) {
            List<SpecialMove> moves = SpecialMoves.forVanillaBody(body);
            if (body.equals("minecraft:creeper")) {
                assertTrue("creepers keep their explosion", moves.isEmpty());
                continue;
            }
            assertEquals(body, 2, moves.size());
            assertFalse(body, moves.get(0) == moves.get(1));
        }
        assertEquals(SpecialMove.VOLLEY, SpecialMoves.forVanillaBody("minecraft:skeleton").get(0));
        assertEquals(SpecialMove.CLEAVE, SpecialMoves.forVanillaBody("minecraft:vindicator").get(0));
        assertEquals(Telegraph.Style.FIRE, SpecialMoves.vanillaStyle("minecraft:magma_cube"));
        assertEquals(Telegraph.Style.DANGER, SpecialMoves.vanillaStyle("minecraft:piglin_brute"));
    }

    /** Every kind of body gets three lord arts of its own kind, all bigger and slower to wind up than its moves. */
    @Test
    public void lordsAddThreeArtsOfTheirKind() {
        assertEquals(List.of(SpecialMove.RAMPAGE, SpecialMove.GREAT_WAVE, SpecialMove.RUIN_CROSS),
                SpecialMoves.lordArts("humanoid", "bandit"));
        assertEquals(List.of(SpecialMove.QUAKE, SpecialMove.RAMPAGE, SpecialMove.METEOR),
                SpecialMoves.lordArts("beast", "wolf"));
        assertEquals(List.of(SpecialMove.QUAKE, SpecialMove.CATACLYSM, SpecialMove.GREAT_WAVE),
                SpecialMoves.lordArts("golem", "treant"));
        assertEquals("casters bring down stones", SpecialMove.METEOR, SpecialMoves.lordArts("humanoid", "void_mage").get(0));
        assertEquals(SpecialMove.METEOR, SpecialMoves.lordArts("flyer", "harpy").get(0));
        for (SpecialMove art : List.of(SpecialMove.QUAKE, SpecialMove.CATACLYSM, SpecialMove.METEOR,
                SpecialMove.RUIN_CROSS, SpecialMove.GREAT_WAVE, SpecialMove.RAMPAGE)) {
            assertTrue(art + " winds up longer", art.windupTicks() >= 30);
        }
        assertEquals("the first bodies: two moves and three arts", 5,
                SpecialMoves.movesOf("humanoid", "bandit", SpecialMoves.LORD_MOVES).size());
        assertTrue("the lords move most often",
                SpecialMoves.cooldownTicks(SpecialMoves.LORD_MOVES) < SpecialMoves.cooldownTicks(2));
    }

    /** The lords' HUD names the move they wind up (special.eternalrealm.<move>, docs/i18n/field_threats.json). */
    @Test
    public void everyMoveHasANameForTheHud() throws java.io.IOException {
        com.google.gson.JsonObject names = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                java.nio.file.Paths.get("docs", "i18n", "field_threats.json"), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (SpecialMove move : SpecialMove.values()) {
            String key = "special.eternalrealm." + move.id();
            assertTrue("missing " + key, names.has(key));
            assertFalse(key, names.getAsJsonObject(key).get("ja").getAsString().isBlank());
        }
    }

    @Test
    public void movesAreReadableBeforeTheyHit() {
        for (SpecialMove move : SpecialMove.values()) {
            assertTrue(move + " wind-up", move.windupTicks() >= SpecialMove.MIN_WINDUP_TICKS);
            assertTrue(move + " damage", move.damageMultiplier() > 1.0 && move.damageMultiplier() <= 2.0);
            assertTrue(move + " range", move.minRange() >= 0.0 && move.maxRange() > move.minRange());
        }
    }

    @Test
    public void elementsColourTheMarkers() {
        assertEquals(Telegraph.Style.FIRE, SpecialMoves.style("salamander"));
        assertEquals(Telegraph.Style.FROST, SpecialMoves.style("ice_golem"));
        assertEquals(Telegraph.Style.DARK, SpecialMoves.style("dark_knight"));
        assertEquals(Telegraph.Style.MAGIC, SpecialMoves.style("storm_spirit"));
        assertEquals(Telegraph.Style.HOLY, SpecialMoves.style("sky_sentinel"));
        assertEquals("physical moves are the red danger marker", Telegraph.Style.DANGER, SpecialMoves.style("bandit"));
    }

    @Test
    public void markersPointAtTheTarget() {
        Telegraph.Style s = Telegraph.Style.DANGER;
        // caster at the origin, target 4 blocks south (+z)
        Telegraph cleave = SpecialMove.CLEAVE.telegraph(s, 0, 64, 0, 0, 64, 4);
        assertTrue(cleave.contains(0, 64, 3));
        assertFalse("behind the caster", cleave.contains(0, 64, -2));
        Telegraph slam = SpecialMove.SLAM.telegraph(s, 0, 64, 0, 0, 64, 4);
        assertTrue("in front", slam.contains(0, 64, SpecialMove.SLAM.b()));
        assertFalse(slam.contains(0, 64, -2));
        Telegraph stomp = SpecialMove.STOMP.telegraph(s, 0, 64, 0, 4, 64, 0);
        assertFalse("safe next to the body", stomp.contains(1, 64, 0));
        assertTrue(stomp.contains(4, 64, 0));
        // a charge to the east goes through the target
        Telegraph charge = SpecialMove.CHARGE.telegraph(s, 0, 64, 0, 6, 64, 0);
        assertTrue(charge.contains(6, 64, 0));
        assertTrue(charge.contains(9, 64, 0));
        assertFalse(charge.contains(-2, 64, 0));
        // a flier 5 blocks up drops a circle on the ground under the target
        Telegraph circle = SpecialMove.CIRCLE.telegraph(s, 0, 64, 0, 10, 64, 10);
        assertEquals(10.0, circle.x(), 1.0E-9);
        assertEquals(64.0, circle.y(), 1.0E-9);
        assertTrue(circle.contains(10, 64, 10));
        Telegraph cross = SpecialMove.STORM_CROSS.telegraph(s, 0, 64, 0, 0, 64, 8);
        assertTrue("one arm points back at the caster", cross.contains(0, 64, 3));
        assertTrue(cross.contains(5, 64, 8));
        assertEquals(SpecialMove.CLEAVE.windupTicks(), cleave.durationTicks());
    }

    @Test
    public void rangesDecideWhenAMoveIsUsed() {
        assertTrue(SpecialMove.CLEAVE.inRange(2.0));
        assertFalse(SpecialMove.CLEAVE.inRange(8.0));
        assertFalse("charges need a run-up", SpecialMove.CHARGE.inRange(1.0));
        assertTrue(SpecialMove.CHARGE.inRange(6.0));
        assertTrue(SpecialMove.CHARGE.dashes());
        assertFalse(SpecialMove.SLAM.dashes());
    }
}
