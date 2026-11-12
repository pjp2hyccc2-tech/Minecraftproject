package com.eternalrealm.npc;

import com.eternalrealm.world.field.Habitats;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
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

    /** Every traveller has a body, a portrait, and everything it says in both languages. */
    @Test
    public void everyTravellerHasABodyAPortraitAndItsLines() throws IOException {
        JsonObject ja = lang("ja_jp.json");
        JsonObject en = lang("en_us.json");
        Set<String> appearances = new HashSet<>();
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            String appearance = traveller.appearance();
            assertTrue("an NPC body for " + appearance, NpcBodies.exists(appearance));
            assertTrue(appearances.add(appearance));
            assertTrue("a texture", Files.isRegularFile(ASSETS.resolve("textures/entity/npc/" + appearance + ".png")));
            Path portrait = ASSETS.resolve("textures/gui/portrait/" + appearance + ".png");
            assertTrue("a portrait " + portrait, Files.isRegularFile(portrait));
            String speaker = traveller.nameKey();
            assertEquals("the dialogue box finds the portrait by the speaker key's last part", appearance,
                    speaker.substring(speaker.lastIndexOf('.') + 1));
            List<String> keys = new ArrayList<>();
            keys.add(speaker);
            keys.addAll(Travellers.meetKeys(traveller));
            for (Travellers.Place place : Travellers.Place.values()) {
                for (int i = 0; i < Travellers.LINES_PER_PLACE; i++) {
                    keys.add(Travellers.talkKey(traveller, place, i));
                }
            }
            assertEquals(traveller + ": two skills", 2, traveller.skills().size());
            for (Travellers.Skill skill : traveller.skills()) {
                keys.add(skill.shoutKey());
            }
            for (String key : keys) {
                assertTrue("ja: " + key, ja.has(key) && !ja.get(key).getAsString().isBlank());
                assertTrue("en: " + key, en.has(key) && !en.get(key).getAsString().isBlank());
            }
        }
        assertTrue(ja.has("message.eternalrealm.traveller.met"));
        assertTrue(ja.has("entity.eternalrealm.traveller"));
    }

    @Test
    public void eachFightsInItsOwnWay() {
        Set<Travellers.Style> styles = EnumSet.noneOf(Travellers.Style.class);
        for (Travellers.Traveller traveller : Travellers.Traveller.values()) {
            assertTrue(styles.add(traveller.style()));
            assertTrue(traveller.range() >= 2.0 && traveller.range() <= 10.0);
            assertTrue(traveller.basicDamage() > 0.0 && traveller.basicDamage() < 0.15);
            assertTrue("at least one skill that fights",
                    traveller.skills().stream().anyMatch(skill -> !skill.heals()));
            for (Travellers.Skill skill : traveller.skills()) {
                assertTrue(skill.cooldownTicks() >= 100);
                assertTrue("a skill takes a share, never a whole monster", skill.power() > 0.0 && skill.power() < 0.5);
            }
        }
        assertTrue(Travellers.Skill.BUBBLE_WARD.heals());
        assertEquals(Travellers.Traveller.GRAUS, Travellers.Traveller.byId("graus").orElseThrow());
        assertTrue(Travellers.Traveller.byId("nobody").isEmpty());
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
    public void plansAndMeetings() {
        assertThrows(IllegalArgumentException.class, () -> new Travellers.Plan(Travellers.Place.FIELD, "", 0L));
        assertThrows(IllegalArgumentException.class, () -> new Travellers.Plan(Travellers.Place.INN, "f1_x", 0L));
        assertEquals(Travellers.Place.SHOP, new Travellers.Plan(Travellers.Place.SHOP, "", 5L).place());
        Set<Travellers.Traveller> met = Travellers.parseMet("sele, bold,nobody");
        assertEquals(EnumSet.of(Travellers.Traveller.SELE, Travellers.Traveller.BOLD), met);
        assertEquals(met, Travellers.parseMet(Travellers.formatMet(met)));
        assertTrue(Travellers.parseMet("").isEmpty());
        assertTrue(Travellers.parseMet(null).isEmpty());
        assertFalse(Travellers.formatMet(met).contains(" "));
        assertEquals(Travellers.talkKey(Travellers.Traveller.BOLD, Travellers.Place.INN, 0),
                Travellers.talkKey(Travellers.Traveller.BOLD, Travellers.Place.INN, Travellers.LINES_PER_PLACE));
        assertEquals("traveller.eternalrealm.mirka.field.2",
                Travellers.talkKey(Travellers.Traveller.MIRKA, Travellers.Place.FIELD, 1));
    }
}
