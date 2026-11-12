package com.eternalrealm.npc;

import com.eternalrealm.monster.MonsterScaling;
import com.eternalrealm.world.field.Habitats;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TravellersTest {
    private static final Path ASSETS = Paths.get("src", "main", "resources", "assets", "eternalrealm");

    private static JsonObject lang(String file) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("lang").resolve(file), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static int pngWidth(Path png) throws IOException {
        try (InputStream in = Files.newInputStream(png); DataInputStream data = new DataInputStream(in)) {
            data.skipNBytes(16); // signature (8), IHDR length and type (8)
            return data.readInt();
        }
    }

    /** Every traveller has a hero body, a sharp portrait, and everything they say in both languages. */
    @Test
    public void everyTravellerHasABodyAPortraitAndTheirLines() throws IOException {
        JsonObject ja = lang("ja_jp.json");
        JsonObject en = lang("en_us.json");
        Set<String> appearances = new HashSet<>();
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            String appearance = traveller.appearance();
            assertTrue("a hero body for " + appearance, HeroBodies.exists(appearance));
            assertTrue(appearances.add(appearance));
            Path texture = ASSETS.resolve("textures/entity/hero/" + appearance + ".png");
            assertTrue("a texture " + texture, Files.isRegularFile(texture));
            assertTrue("painted at two texels per unit", pngWidth(texture) >= 256);
            assertTrue("something shines", Files.isRegularFile(ASSETS.resolve("textures/entity/hero/" + appearance
                    + "_glow.png")));
            Path portrait = ASSETS.resolve("textures/gui/portrait/" + appearance + ".png");
            assertTrue("a portrait " + portrait, Files.isRegularFile(portrait));
            assertEquals("a sharp portrait", 96, pngWidth(portrait));
            String speaker = traveller.nameKey();
            assertEquals("the dialogue box finds the portrait by the speaker key's last part", appearance,
                    speaker.substring(speaker.lastIndexOf('.') + 1));
            List<String> keys = new ArrayList<>();
            keys.add(speaker);
            keys.add(traveller.titleKey());
            keys.addAll(Travellers.meetKeys(traveller));
            for (String what : List.of("ask", "accept", "decline", "farewell", "down", "up")) {
                keys.add(Travellers.lineKey(traveller, what));
            }
            for (Travellers.Place place : Travellers.Place.values()) {
                for (int i = 0; i < Travellers.LINES_PER_PLACE; i++) {
                    keys.add(Travellers.talkKey(traveller, place, i));
                }
            }
            for (int i = 0; i < Travellers.PARTY_LINES; i++) {
                keys.add(Travellers.partyKey(traveller, i));
            }
            for (Travellers.Move move : traveller.moves()) {
                keys.add(move.nameKey());
                if (move.kind() != Travellers.Kind.ATTACK) {
                    keys.add(move.shoutKey());
                }
            }
            for (String key : keys) {
                assertTrue("ja: " + key, ja.has(key) && !ja.get(key).getAsString().isBlank());
                assertTrue("en: " + key, en.has(key) && !en.get(key).getAsString().isBlank());
            }
        }
        for (String key : List.of("message.eternalrealm.traveller.met", "message.eternalrealm.traveller.joined",
                "message.eternalrealm.traveller.left", "entity.eternalrealm.traveller",
                "traveller.eternalrealm.choice.invite", "traveller.eternalrealm.choice.later",
                "traveller.eternalrealm.choice.stay", "traveller.eternalrealm.choice.part",
                "screen.eternalrealm.party.part", "dialogue.eternalrealm.hint.choice")) {
            assertTrue("ja: " + key, ja.has(key));
            assertTrue("en: " + key, en.has(key));
        }
        assertFalse("the old travellers are gone", ja.has("traveller.eternalrealm.traveller_bold"));
    }

    /** Two attacks, two skills and an ultimate each, every one with its own animation and its blows on time. */
    @Test
    public void eachHasFiveMovesInTheirOwnRole() {
        Set<Travellers.Role> roles = EnumSet.noneOf(Travellers.Role.class);
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            assertTrue(roles.add(traveller.role()));
            List<Travellers.Move> moves = traveller.moves();
            assertEquals(traveller + ": five moves", 5, moves.size());
            assertEquals(2, moves.stream().filter(m -> m.kind() == Travellers.Kind.ATTACK).count());
            assertEquals(2, moves.stream().filter(m -> m.kind() == Travellers.Kind.SKILL).count());
            assertEquals(1, moves.stream().filter(m -> m.kind() == Travellers.Kind.ULTIMATE).count());
            Set<Integer> slots = new HashSet<>();
            for (Travellers.Move move : moves) {
                assertTrue("one animation per move", slots.add(move.slot()));
                int[] hits = move.hitTicks();
                assertTrue(move + " lands at least one blow", hits.length >= 1);
                for (int i = 0; i < hits.length; i++) {
                    assertTrue(move + " blow inside the animation", hits[i] > 0 && hits[i] <= move.lengthTicks());
                    if (i > 0) {
                        assertTrue(move + " blows in order", hits[i] >= hits[i - 1]);
                    }
                }
                switch (move.kind()) {
                    case ATTACK -> assertEquals(0, move.cooldownTicks());
                    case SKILL -> assertTrue(move.cooldownTicks() >= 100 && move.cooldownTicks() <= 600);
                    case ULTIMATE -> assertTrue("an ultimate is rare", move.cooldownTicks() >= 1000);
                }
                assertTrue(move.reach() > 0.0 && move.radius() > 0.0 && move.power() > 0.0);
                assertEquals(move, traveller.move(move.kind(), move.second()));
            }
            assertTrue("the ultimate is the longest show",
                    traveller.move(Travellers.Kind.ULTIMATE, false).lengthTicks() >= 50);
            assertTrue("stand at the level cap", Travellers.LEVEL == MonsterScaling.MAX_LEVEL);
            assertTrue("a legend's health", traveller.maxHealth() >= 600.0 && traveller.maxHealth() <= 1024.0);
            assertTrue("a legend's toughness", traveller.damageTaken() > 0.0 && traveller.damageTaken() <= 0.35);
        }
        for (Travellers.Traveller other : Travellers.Traveller.values()) {
            if (other != Travellers.Traveller.SIEGWALD) {
                assertTrue("the knight who turned a dragon is the toughest",
                        Travellers.Traveller.SIEGWALD.maxHealth() > other.maxHealth()
                                && Travellers.Traveller.SIEGWALD.damageTaken() < other.damageTaken());
            }
        }
        assertTrue(Travellers.Move.MAR_SANCTUARY.heals());
        assertEquals(Travellers.Traveller.KAYLEN, Travellers.Traveller.byId("kaylen").orElseThrow());
        assertTrue(Travellers.Traveller.byId("bold").isEmpty());
    }

    /** Their blows are measured against the enemy's level; a boss takes a third; common monsters fall to an ultimate. */
    @Test
    public void strengthFollowsTheirTitles() {
        double reference = MonsterScaling.referenceLightHit(30);
        Travellers.Move bolt = Travellers.Move.MAR_HOLY_BOLT;
        assertEquals(reference * bolt.power(), Travellers.damage(bolt, 30, false, 1.0), 1.0E-9);
        assertEquals(reference * bolt.power() * Travellers.BOSS_SHARE, Travellers.damage(bolt, 30, true, 1.0),
                1.0E-9);
        assertEquals(reference * bolt.power() * 1.8, Travellers.damage(bolt, 30, false, 1.8), 1.0E-9);
        assertEquals("levels are clamped", Travellers.damage(bolt, MonsterScaling.MAX_LEVEL, false, 1.0),
                Travellers.damage(bolt, 500, false, 1.0), 1.0E-9);
        assertTrue(Travellers.damage(bolt, 80, false, 1.0) > Travellers.damage(bolt, 10, false, 1.0));
        // a standard monster takes about four of a level-matched player's hits; an ultimate is worth far more
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            Travellers.Move ultimate = traveller.move(Travellers.Kind.ULTIMATE, false);
            double total = ultimate.power() * ultimate.hitTicks().length;
            assertTrue(traveller + "'s ultimate", total >= 3.0);
        }
        assertTrue("the dragon stroke is one terrible blow", Travellers.Move.SIEG_DRAGON_STROKE.power() >= 8.0);
        assertTrue("the canopy covers the field", Travellers.Move.ROS_THOUSAND_NIGHTS.radius() >= 10.0);
        // monsters barely scratch them; the oath makes the knight harder still
        assertEquals(0.15 * 10.0, Travellers.damageTaken(Travellers.Traveller.SIEGWALD, 10.0, false), 1.0E-9);
        assertTrue(Travellers.damageTaken(Travellers.Traveller.SIEGWALD, 10.0, true)
                < Travellers.damageTaken(Travellers.Traveller.SIEGWALD, 10.0, false));
        assertEquals(0.0, Travellers.damageTaken(Travellers.Traveller.MARIEL, -3.0, false), 0.0);
    }

    private static Travellers.Situation calm(double distance) {
        return new Travellers.Situation(distance, 1, false, 1.0, 0, false, 0);
    }

    /** What they choose: the sister heals first, the knight draws the enemies, the ultimate when it counts. */
    @Test
    public void eachChoosesTheirMovesInTheirRole() {
        java.util.function.Predicate<Travellers.Move> all = move -> true;
        java.util.function.Predicate<Travellers.Move> attacksOnly = move -> move.kind() == Travellers.Kind.ATTACK;
        // ordinary attacks alternate, and only in reach
        assertEquals(Optional.of(Travellers.Move.KAY_SLASH),
                Travellers.choose(Travellers.Traveller.KAYLEN, calm(2.0), attacksOnly, false));
        assertEquals(Optional.of(Travellers.Move.KAY_CROSS),
                Travellers.choose(Travellers.Traveller.KAYLEN, calm(2.0), attacksOnly, true));
        assertTrue(Travellers.choose(Travellers.Traveller.KAYLEN, calm(6.0), attacksOnly, false).isEmpty());
        assertTrue("nothing reaches that far",
                Travellers.choose(Travellers.Traveller.ROSALIND, calm(30.0), all, false).isEmpty());
        // the flash step closes any gap
        assertEquals(Optional.of(Travellers.Move.KAY_FLASH_STEP),
                Travellers.choose(Travellers.Traveller.KAYLEN, calm(8.0), all, false));
        // the ultimate: a crowd, a boss, or a party in trouble
        Travellers.Situation crowd = new Travellers.Situation(5.0, 4, false, 1.0, 0, false, 0);
        assertEquals(Optional.of(Travellers.Move.ROS_THOUSAND_NIGHTS),
                Travellers.choose(Travellers.Traveller.ROSALIND, crowd, all, false));
        Travellers.Situation boss = new Travellers.Situation(2.5, 1, true, 1.0, 0, false, 0);
        assertEquals(Optional.of(Travellers.Move.SIEG_DRAGON_STROKE),
                Travellers.choose(Travellers.Traveller.SIEGWALD, boss, all, false));
        assertEquals("one at a time: a skill",
                Optional.of(Travellers.Move.ROS_STARFALL), Travellers.choose(Travellers.Traveller.ROSALIND, calm(5.0),
                        all, false));
        // the knight's oath when enemies go for others
        Travellers.Situation others = new Travellers.Situation(2.0, 1, false, 1.0, 0, false, 2);
        assertEquals(Optional.of(Travellers.Move.SIEG_OATH),
                Travellers.choose(Travellers.Traveller.SIEGWALD, others, move -> move != Travellers.Move
                        .SIEG_DRAGON_STROKE, false));
        // the sister: heals the hurt (even with no enemy in reach), raises the fallen with her prayer
        Travellers.Situation hurt = new Travellers.Situation(40.0, 0, false, 0.5, 1, false, 0);
        assertEquals(Optional.of(Travellers.Move.MAR_SANCTUARY),
                Travellers.choose(Travellers.Traveller.MARIEL, hurt, all, false));
        Travellers.Situation fallen = new Travellers.Situation(40.0, 0, false, 0.9, 0, true, 0);
        assertEquals(Optional.of(Travellers.Move.MAR_KINGDOM_REBORN),
                Travellers.choose(Travellers.Traveller.MARIEL, fallen, all, false));
        assertEquals("without her prayer ready: the sanctuary", Optional.of(Travellers.Move.MAR_SANCTUARY),
                Travellers.choose(Travellers.Traveller.MARIEL, fallen,
                        move -> move != Travellers.Move.MAR_KINGDOM_REBORN, false));
        assertEquals("nobody hurt: she fights", Optional.of(Travellers.Move.MAR_PURIFICATION),
                Travellers.choose(Travellers.Traveller.MARIEL, calm(5.0), all, false));
    }

    @Test
    public void companionsKeepTheirOwnPlaceAroundThePlayer() {
        Set<String> spots = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            double[] spot = Travellers.formation(i);
            assertTrue("behind the player", spot[1] > 0.0);
            assertTrue("not on top of the player", Math.hypot(spot[0], spot[1]) >= 1.5);
            assertTrue("a place of their own", spots.add(spot[0] + "/" + spot[1]));
        }
        assertTrue(Travellers.CATCH_UP > Travellers.LEASH && Travellers.LEASH > Travellers.GUARD_RADIUS);
        assertTrue(Travellers.DOWN_TICKS >= 200 && Travellers.STAND_UP_HEALTH > 0.0 && Travellers.STAND_UP_HEALTH < 1.0);
    }

    @Test
    public void theRoundGoesFromTheFieldToTownAndBack() {
        for (double roll : new double[]{0.0, 0.3, 0.7, 0.99}) {
            assertNotEquals("never two field trips in a row", Travellers.Place.FIELD,
                    Travellers.nextPlace(Travellers.Place.FIELD, roll));
            assertEquals("out again after the inn", Travellers.Place.FIELD,
                    Travellers.nextPlace(Travellers.Place.INN, roll));
        }
        assertEquals(Travellers.Place.INN, Travellers.nextPlace(Travellers.Place.SHOP, 0.2));
        assertEquals(Travellers.Place.FIELD, Travellers.nextPlace(Travellers.Place.SHOP, 0.8));
        assertEquals(20 * 60 * 6, Travellers.stay(Travellers.Place.FIELD, false, 0.0));
        assertEquals(20 * 60 * 10, Travellers.stay(Travellers.Place.FIELD, false, 1.0));
        assertEquals(20 * 60 * 2, Travellers.stay(Travellers.Place.SHOP, true, 0.0));
        assertTrue("longer at the inn at night",
                Travellers.stay(Travellers.Place.INN, true, 0.5) > Travellers.stay(Travellers.Place.INN, false, 0.5));
    }

    @Test
    public void mostFieldTripsGoNearThePlayerButNeverBackToTheSameHabitat() {
        List<Habitats.Habitat> all = Habitats.all();
        Habitats.Habitat here = all.get(0);
        Random random = new Random(7);
        int near = 0;
        for (int i = 0; i < 400; i++) {
            Habitats.Habitat picked = Travellers.pickHabitat(all, here.id(), here.x(), here.z(), random).orElseThrow();
            assertNotEquals("never the one it just left", here.id(), picked.id());
            if (picked.distanceTo(here.x(), here.z()) <= Travellers.NEAR_PLAYER) {
                near++;
            }
        }
        assertTrue("most trips near the player: " + near, near > 200);
        assertTrue("but not all: " + near, near < 400);
        assertTrue(Travellers.pickHabitat(List.of(), "", 0.0, 0.0, random).isEmpty());
        assertTrue("nothing but the one it left", Travellers.pickHabitat(List.of(here), here.id(), 0.0, 0.0, random)
                .isEmpty());
    }

    @Test
    public void plansAndStoredLists() {
        assertThrows(IllegalArgumentException.class, () -> new Travellers.Plan(Travellers.Place.FIELD, "", 0L));
        assertThrows(IllegalArgumentException.class, () -> new Travellers.Plan(Travellers.Place.INN, "f1_x", 0L));
        assertEquals(Travellers.Place.SHOP, new Travellers.Plan(Travellers.Place.SHOP, "", 5L).place());
        Set<Travellers.Traveller> party = Travellers.parse("mariel, rosalind,bold");
        assertEquals(EnumSet.of(Travellers.Traveller.MARIEL, Travellers.Traveller.ROSALIND), party);
        assertEquals(party, Travellers.parse(Travellers.format(party)));
        assertTrue(Travellers.parse("").isEmpty());
        assertTrue(Travellers.parse(null).isEmpty());
        assertFalse(Travellers.format(party).contains(" "));
        assertEquals(Travellers.talkKey(Travellers.Traveller.SIEGWALD, Travellers.Place.INN, 0),
                Travellers.talkKey(Travellers.Traveller.SIEGWALD, Travellers.Place.INN, Travellers.LINES_PER_PLACE));
        assertEquals("traveller.eternalrealm.kaylen.field.2",
                Travellers.talkKey(Travellers.Traveller.KAYLEN, Travellers.Place.FIELD, 1));
        assertEquals("traveller.eternalrealm.mariel.party.1", Travellers.partyKey(Travellers.Traveller.MARIEL, 4));
        assertEquals("traveller.eternalrealm.rosalind.shout.thousand_nights",
                Travellers.Move.ROS_THOUSAND_NIGHTS.shoutKey());
    }
}
