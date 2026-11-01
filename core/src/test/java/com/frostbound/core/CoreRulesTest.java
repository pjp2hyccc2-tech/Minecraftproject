package com.frostbound.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostbound.core.city.ActionResult;
import com.frostbound.core.city.Building;
import com.frostbound.core.city.BuildingTypes;
import com.frostbound.core.city.City;
import com.frostbound.core.city.WorldAccess;
import com.frostbound.core.heat.HeatNetwork;
import com.frostbound.core.resources.Cost;
import com.frostbound.core.resources.ResourceLedger;
import com.frostbound.core.resources.ResourceType;
import com.frostbound.core.save.SaveMigrations;
import com.frostbound.core.sim.SimClock;
import com.frostbound.core.util.Tree;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoreRulesTest {

    private static City newCity(long seed) {
        return City.found(seed, Difficulty.NORMAL, 20, "Test", false, 0, 64, 0, WorldAccess.NONE);
    }

    @Test
    void clockRunsTheSameNumberOfStepsAtEverySpeed() {
        SimClock one = new SimClock();
        SimClock three = new SimClock();
        three.setSpeed(3);
        long a = 0;
        long b = 0;
        for (int i = 0; i < 24_000 * 3; i++) {
            a += one.stepsForServerTick(20, false);
        }
        for (int i = 0; i < 24_000; i++) {
            b += three.stepsForServerTick(20, false);
        }
        assertEquals(SimClock.STEPS_PER_DAY * 3L, a);
        assertEquals(a, b);
    }

    @Test
    void pausedClockDoesNotAdvance() {
        SimClock c = new SimClock();
        c.setPaused(true);
        int n = 0;
        for (int i = 0; i < 1000; i++) {
            n += c.stepsForServerTick(20, false);
        }
        assertEquals(0, n);
        c.setPaused(false);
        for (int i = 0; i < 1000; i++) {
            n += c.stepsForServerTick(20, true);
        }
        assertEquals(0, n);
    }

    @Test
    void dayStartsAtSixInTheMorning() {
        SimClock c = new SimClock();
        assertEquals(1, c.day());
        assertEquals(6, c.hourOfDay());
        c.setStep(SimClock.STEPS_PER_DAY + SimClock.STEPS_PER_HOUR * 6);
        assertEquals(2, c.day());
        assertEquals(12, c.hourOfDay());
    }

    @Test
    void ledgerNeverGoesNegativeOrOverCapacity() {
        ResourceLedger l = new ResourceLedger();
        l.set(ResourceType.COAL, 50 * ResourceLedger.MILLI);
        long taken = l.take(ResourceType.COAL, 80 * ResourceLedger.MILLI);
        assertEquals(50 * ResourceLedger.MILLI, taken);
        assertEquals(0, l.amountMilli(ResourceType.COAL));
        l.add(ResourceType.COAL, 10_000 * ResourceLedger.MILLI);
        assertEquals(l.capacityMilli(ResourceType.COAL), l.amountMilli(ResourceType.COAL));
        assertFalse(l.spend(Cost.of(ResourceType.IRON, 1)));
    }

    @Test
    void heatEfficiencyFallsOffOutsideTheCore() {
        assertEquals(1.0, HeatNetwork.eta(10, 40), 1e-9);
        assertEquals(1.0, HeatNetwork.eta(24, 40), 1e-9);
        assertEquals(0.5, HeatNetwork.eta(40, 40), 1e-9);
        assertEquals(0.0, HeatNetwork.eta(41, 40), 1e-9);
        assertEquals(0.0, HeatNetwork.eta(5, 0), 1e-9);
    }

    @Test
    void foundingCreatesTheStartingCity() {
        City c = newCity(1);
        assertEquals(30, c.population().alive);
        assertEquals(5, c.buildings().size());
        assertTrue(c.ledger().amount(ResourceType.COAL) > 0);
        assertNotNull(c.building(1));
        assertEquals(BuildingTypes.GENERATOR, c.building(1).type());
    }

    @Test
    void simulationIsDeterministic() {
        City a = newCity(7);
        City b = newCity(7);
        for (int i = 0; i < SimClock.STEPS_PER_DAY * 5; i++) {
            a.step();
            b.step();
        }
        assertEquals(a.save(), b.save());
    }

    @Test
    void saveAndLoadRestoresTheWholeState() {
        City a = newCity(11);
        for (int i = 0; i < SimClock.STEPS_PER_DAY * 3; i++) {
            a.step();
        }
        Map<String, Object> tree = a.save();
        Tree.clearRepairs();
        City b = City.load(tree, WorldAccess.NONE);
        assertEquals(tree, b.save());
        assertTrue(Tree.repairs().isEmpty(), Tree.repairs().toString());
        for (int i = 0; i < SimClock.STEPS_PER_DAY; i++) {
            a.step();
            b.step();
        }
        assertEquals(a.save(), b.save());
    }

    @Test
    @SuppressWarnings("unchecked")
    void corruptedValuesAreRepaired() {
        City a = newCity(3);
        Map<String, Object> tree = a.save();
        Map<String, Object> res = new LinkedHashMap<>((Map<String, Object>) tree.get("resources"));
        Map<String, Object> amounts = new LinkedHashMap<>((Map<String, Object>) res.get("amounts"));
        amounts.put("coal", -500L);
        res.put("amounts", amounts);
        tree.put("resources", res);
        Map<String, Object> gen = new LinkedHashMap<>((Map<String, Object>) tree.get("generator"));
        gen.put("level", 99);
        tree.put("generator", gen);
        Tree.clearRepairs();
        City b = City.load(tree, WorldAccess.NONE);
        assertEquals(0, b.ledger().amount(ResourceType.COAL));
        assertTrue(b.generator().level() <= 4);
        assertFalse(Tree.repairs().isEmpty());
    }

    @Test
    void newerSchemaIsRefused() {
        Map<String, Object> tree = newCity(5).save();
        tree.put("schema", SaveMigrations.CURRENT + 1);
        assertThrows(SaveMigrations.NewerSaveException.class, () -> City.load(tree, WorldAccess.NONE));
    }

    @Test
    void placementIsValidatedAndPaidFor() {
        City c = newCity(9);
        int woodBefore = c.ledger().amount(ResourceType.WOOD);
        assertNotNull(c.checkPlacement("tent", 0, 64, 0, 0), "overlaps the generator");
        assertNotNull(c.checkPlacement("tent", 500, 64, 500, 0), "too far");
        assertNotNull(c.checkPlacement("hospital", 30, 64, 30, 0), "locked");
        assertNull(c.checkPlacement("tent", 30, 64, -30, 0));
        ActionResult r = c.placeBuilding("tent", 30, 64, -30, 0);
        assertTrue(r.ok());
        assertEquals(woodBefore - 10, c.ledger().amount(ResourceType.WOOD));
        Building tent = c.buildings().get(c.buildings().size() - 1);
        assertEquals(Building.State.CONSTRUCTION, tent.state);
        for (int i = 0; i < SimClock.STEPS_PER_DAY; i++) {
            c.step();
        }
        assertEquals(Building.State.ACTIVE, tent.state);
    }

    @Test
    void generatorBurnsCoalAndHeatsHomes() {
        City c = newCity(13);
        int coal = c.ledger().amount(ResourceType.COAL);
        for (int i = 0; i < SimClock.STEPS_PER_HOUR * 6; i++) {
            c.step();
        }
        assertTrue(c.generator().running());
        assertTrue(c.ledger().amount(ResourceType.COAL) + c.generator().bufferMilli() / 1000 < coal + 60);
        double outside = c.outsideTemperature();
        Building shelter = c.buildings().stream().filter(b -> b.typeId.equals("shelter")).findFirst().orElseThrow();
        assertTrue(shelter.temperature > outside + 20, "heated: " + shelter.temperature + " vs " + outside);
        c.setGeneratorLevel(0);
        for (int i = 0; i < SimClock.STEPS_PER_HOUR * 12; i++) {
            c.step();
        }
        assertTrue(shelter.temperature < outside + 12);
    }

    @Test
    void lawsResearchAndEventsWork() {
        City c = newCity(17);
        assertTrue(c.enactLaw("extended_shift").ok());
        assertFalse(c.enactLaw("food_rationing").ok(), "one law per day");
        assertEquals(22, c.modifiers().shiftEndHour);
        assertTrue(c.startResearch("wooden_houses").ok());
        c.addResearchPoints(1000);
        assertTrue(c.research().isCompleted("wooden_houses"));
        assertTrue(c.modifiers().unlocked.contains("wooden_house"));
        c.events().trigger(c, "survivors_found", List.of("4"));
        assertNotNull(c.events().pending());
        int before = c.population().alive;
        assertTrue(c.answerEvent(0).ok());
        assertEquals(before + 4, c.population().alive);
    }
}
