package com.eternalrealm.mount;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The individual values with their floor, the grades, the looks and the stablemaster's four offers. */
public class MountIndividualityTest {
    @Test
    public void valuesHaveAFloorAndScaleTheSpeciesByAtMostEightPercent() {
        assertEquals(30, MountTraits.MIN);
        assertEquals(100, MountTraits.MAX);
        assertEquals(0.92, MountTraits.factor(MountTraits.MIN), 1e-9);
        assertEquals(1.0, MountTraits.factor(MountTraits.STANDARD_VALUE), 1e-9);
        assertEquals(1.08, MountTraits.factor(MountTraits.MAX), 1e-9);
        assertEquals(MountTraits.factor(MountTraits.MIN), MountTraits.factor(-5), 1e-9); // clamped
        assertEquals(MountTraits.factor(MountTraits.MAX), MountTraits.factor(250), 1e-9);
        for (int[] bad : new int[][] {{29, 50, 50}, {50, 101, 50}, {50, 50, 0}}) {
            try {
                new MountTraits(bad[0], bad[1], bad[2]);
                fail("accepted " + bad[0] + "/" + bad[1] + "/" + bad[2]);
            } catch (IllegalArgumentException expected) {
                // the floor and the top are enforced
            }
        }
        SplittableRandom random = new SplittableRandom(7);
        boolean floor = false;
        boolean top = false;
        for (int i = 0; i < 20_000; i++) {
            MountTraits traits = MountTraits.roll(random);
            for (int value : new int[] {traits.speed(), traits.acceleration(), traits.lift()}) {
                assertTrue(value >= MountTraits.MIN && value <= MountTraits.MAX);
                floor |= value == MountTraits.MIN;
                top |= value == MountTraits.MAX;
            }
        }
        assertTrue("both ends are rolled", floor && top);
    }

    @Test
    public void gradesComeFromTheSumWithRareTopGrades() {
        assertEquals(MountTraits.Grade.C, MountTraits.Grade.of(90));
        assertEquals(MountTraits.Grade.C, MountTraits.Grade.of(173));
        assertEquals(MountTraits.Grade.B, MountTraits.Grade.of(174));
        assertEquals(MountTraits.Grade.B, MountTraits.STANDARD.grade());
        assertEquals(MountTraits.Grade.A, MountTraits.Grade.of(222));
        assertEquals(MountTraits.Grade.S, MountTraits.Grade.of(255));
        assertEquals(MountTraits.Grade.SS, MountTraits.Grade.of(279));
        assertEquals(MountTraits.Grade.SS, new MountTraits(100, 100, 100).grade());
        for (MountTraits.Grade grade : MountTraits.Grade.values()) {
            assertEquals(java.util.Optional.of(grade), MountTraits.Grade.byId(grade.id()));
        }
        // the odds the docs give: about 28 % C, 48 % B, 19 % A, 4 % S and one in 177 SS
        Map<MountTraits.Grade, Integer> counts = new EnumMap<>(MountTraits.Grade.class);
        SplittableRandom random = new SplittableRandom(11);
        int rolls = 400_000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(MountTraits.roll(random).grade(), 1, Integer::sum);
        }
        assertEquals(0.282, share(counts, MountTraits.Grade.C, rolls), 0.01);
        assertEquals(0.480, share(counts, MountTraits.Grade.B, rolls), 0.01);
        assertEquals(0.189, share(counts, MountTraits.Grade.A, rolls), 0.01);
        assertEquals(0.0427, share(counts, MountTraits.Grade.S, rolls), 0.004);
        assertEquals(0.00566, share(counts, MountTraits.Grade.SS, rolls), 0.001);
    }

    private static double share(Map<MountTraits.Grade, Integer> counts, MountTraits.Grade grade, int rolls) {
        return counts.getOrDefault(grade, 0) / (double) rolls;
    }

    @Test
    public void everyCoatIsATextureOfItsModelAndTheClassicLookComesFirst() {
        Path textures = Paths.get("src", "main", "resources", "assets", "eternalrealm", "textures", "entity");
        for (MountKind kind : MountKind.values()) {
            if (kind.family() == MountFamily.BIKE) {
                continue;
            }
            assertTrue(MountLooks.varies(kind));
            Set<String> ids = new HashSet<>();
            boolean rare = false;
            for (MountLooks.Coat coat : MountLooks.coats(kind)) {
                assertTrue(coat.id(), ids.add(coat.id()));
                assertTrue(coat.weight() > 0);
                rare |= coat.rare();
                Path texture = textures.resolve("mount_" + kind.id()).resolve(coat.id() + ".png");
                assertTrue("texture of " + kind.id() + "/" + coat.id(), Files.isRegularFile(texture));
                assertTrue("glow of " + kind.id() + "/" + coat.id(),
                        Files.isRegularFile(texture.resolveSibling(coat.id() + "_glow.png")));
            }
            assertTrue("a few coats are rare", rare);
            assertFalse("the classic coat is a common one", MountLooks.coats(kind).get(0).rare());
            assertTrue(MountLooks.shapes(kind).size() > 1);
            assertTrue(kind + ": several coats", MountLooks.coats(kind).size() >= 5);
            // the manes and wings are the family's
            assertEquals(MountLooks.shapes(kind.family() == MountFamily.HORSE ? MountKind.HORSE : MountKind.GRIFFIN),
                    MountLooks.shapes(kind));
            Path hud = Paths.get("src", "main", "resources", "assets", "eternalrealm", "textures", "gui", "hud",
                    "mount_" + kind.id() + ".png");
            assertTrue("HUD icon of " + kind.id(), Files.isRegularFile(hud));
            assertEquals(new MountLook(MountLooks.coats(kind).get(0).id(), MountLooks.shapes(kind).get(0).id()),
                    MountLooks.classic(kind));
        }
        assertFalse("the arcbike is one machine", MountLooks.varies(MountKind.BIKE));
        assertTrue(Files.isRegularFile(Paths.get("src", "main", "resources", "assets", "eternalrealm", "textures", "gui",
                "hud", "mount_bike.png")));
    }

    @Test
    public void everyBreedRollsAllItsCoatsByWeightAndKeepsItsValuesAndLookInJson() {
        SplittableRandom random = new SplittableRandom(21);
        for (MountKind kind : MountKind.values()) {
            if (!MountLooks.varies(kind)) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 4_000; i++) {
                MountIndividual mount = MountIndividual.roll(UUID.randomUUID(), kind, random);
                assertTrue(MountLooks.valid(kind, mount.look()));
                seen.add(mount.look().coat());
                if (i < 20) {
                    assertEquals(mount, MountIndividual.fromJson(mount.toJson()).orElseThrow());
                }
            }
            assertEquals(kind + " rolls every coat", MountLooks.coats(kind).size(), seen.size());
            // a breed's individual moves by its own numbers scaled by its values
            MountIndividual best = new MountIndividual(UUID.randomUUID(), kind, MountLooks.classic(kind),
                    new MountTraits(100, 100, 100));
            assertEquals(kind.stats().topSpeed() * 1.08, best.stats().topSpeed(), 1e-9);
            // a coat of another breed is not this breed's: the classic look instead
            MountLook foreign = new MountLook("night", "flowing");
            if (!MountLooks.valid(kind, foreign)) {
                assertEquals(MountLooks.classic(kind), new MountIndividual(UUID.randomUUID(), kind, foreign,
                        MountTraits.STANDARD).look());
            }
        }
    }

    @Test
    public void looksAreRolledByWeight() {
        SplittableRandom random = new SplittableRandom(3);
        Map<String, Integer> coats = new HashMap<>();
        int rolls = 100_000;
        for (int i = 0; i < rolls; i++) {
            MountLook look = MountLooks.roll(MountKind.HORSE, random);
            assertTrue(look.encode(), MountLooks.valid(MountKind.HORSE, look));
            coats.merge(look.coat(), 1, Integer::sum);
        }
        int total = 0;
        for (MountLooks.Coat coat : MountLooks.coats(MountKind.HORSE)) {
            total += coat.weight();
        }
        for (MountLooks.Coat coat : MountLooks.coats(MountKind.HORSE)) {
            assertEquals(coat.id(), coat.weight() / (double) total, coats.getOrDefault(coat.id(), 0) / (double) rolls,
                    0.01);
        }
        assertEquals(new MountLook("night", "long"), MountLook.decode(new MountLook("night", "long").encode()));
    }

    @Test
    public void anIndividualMovesByItsValuesExceptTheArcbike() {
        MountKind.MountStats base = MountKind.HORSE.stats();
        MountIndividual fast = new MountIndividual(UUID.randomUUID(), MountKind.HORSE,
                MountLooks.classic(MountKind.HORSE), new MountTraits(100, 30, 65));
        assertEquals(base.topSpeed() * 1.08, fast.stats().topSpeed(), 1e-9);
        assertEquals(base.acceleration() * 0.92, fast.stats().acceleration(), 1e-9);
        assertEquals(base.jump(), fast.stats().jump(), 1e-9);
        MountKind.MountStats sky = MountKind.GRIFFIN.stats();
        MountIndividual lifter = new MountIndividual(UUID.randomUUID(), MountKind.GRIFFIN,
                MountLooks.classic(MountKind.GRIFFIN), new MountTraits(65, 65, 100));
        assertEquals(sky.climb() * 1.08, lifter.stats().climb(), 1e-9);
        assertEquals(sky.glide() / 1.08, lifter.stats().glide(), 1e-9); // a better flier sinks slower
        assertEquals(sky.groundSpeed(), lifter.stats().groundSpeed(), 1e-9);
        // the arcbike is a machine: always the standard values and its own numbers
        MountIndividual bike = new MountIndividual(UUID.randomUUID(), MountKind.BIKE, new MountLook("x", "y"),
                new MountTraits(100, 100, 100));
        assertEquals(MountTraits.STANDARD, bike.traits());
        assertEquals(MountLooks.classic(MountKind.BIKE), bike.look());
        assertEquals(MountKind.BIKE.stats(), bike.stats());
        // a look the kind does not have becomes the classic one
        assertEquals(MountLooks.classic(MountKind.HORSE), new MountIndividual(UUID.randomUUID(), MountKind.HORSE,
                new MountLook("emerald", "spiky"), MountTraits.STANDARD).look());
    }

    @Test
    public void individualsSurviveTheirJsonAndReadLeniently() {
        MountIndividual griffin = MountIndividual.roll(UUID.randomUUID(), MountKind.GRIFFIN, new SplittableRandom(5));
        assertEquals(griffin, MountIndividual.fromJson(griffin.toJson()).orElseThrow());
        JsonObject wild = griffin.toJson();
        wild.addProperty("speed", 400);
        wild.addProperty("lift", -3);
        MountIndividual clamped = MountIndividual.fromJson(wild).orElseThrow();
        assertEquals(MountTraits.MAX, clamped.traits().speed());
        assertEquals(MountTraits.MIN, clamped.traits().lift());
        JsonObject unknown = griffin.toJson();
        unknown.addProperty("kind", "wyvern");
        assertTrue(MountIndividual.fromJson(unknown).isEmpty());
        assertTrue(MountIndividual.fromJson(null).isEmpty());
    }

    @Test
    public void theStablemasterShowsFourDifferentOffersOfEveryBreed() {
        SplittableRandom random = new SplittableRandom(9);
        for (MountKind kind : MountKind.values()) {
            if (!MountLooks.varies(kind)) {
                continue;
            }
            List<MountIndividual> offers = MountOffers.roll(kind, random);
            assertEquals(MountRules.OFFERS, offers.size());
            assertEquals(4, MountRules.OFFERS);
            Set<UUID> ids = new HashSet<>();
            for (MountIndividual offer : offers) {
                assertEquals(kind, offer.kind());
                assertTrue(ids.add(offer.id()));
                assertTrue(MountLooks.valid(kind, offer.look()));
            }
        }
    }
}
