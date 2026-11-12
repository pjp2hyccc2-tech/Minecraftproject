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

public class WanderersTest {
    private static final Path ASSETS = Paths.get("src", "main", "resources", "assets", "eternalrealm");

    private static JsonObject lang(String file) throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("lang").resolve(file), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    /** Every wanderer has a body, a portrait, and everything it says in both languages. */
    @Test
    public void everyWandererHasABodyAPortraitAndItsLines() throws IOException {
        JsonObject ja = lang("ja_jp.json");
        JsonObject en = lang("en_us.json");
        Set<String> appearances = new HashSet<>();
        for (Wanderers.Wanderer wanderer : Wanderers.Wanderer.values()) {
            String appearance = wanderer.appearance();
            assertTrue("an NPC body for " + appearance, NpcBodies.exists(appearance));
            assertTrue(appearances.add(appearance));
            assertTrue("a texture", Files.isRegularFile(ASSETS.resolve("textures/entity/npc/" + appearance + ".png")));
            Path portrait = ASSETS.resolve("textures/gui/portrait/" + appearance + ".png");
            assertTrue("a portrait " + portrait, Files.isRegularFile(portrait));
            String speaker = wanderer.nameKey();
            assertEquals("the dialogue box finds the portrait by the speaker key's last part", appearance,
                    speaker.substring(speaker.lastIndexOf('.') + 1));
            List<String> keys = new ArrayList<>();
            keys.add(speaker);
            keys.addAll(Wanderers.meetKeys(wanderer));
            for (Wanderers.Place place : Wanderers.Place.values()) {
                for (int i = 0; i < Wanderers.LINES_PER_PLACE; i++) {
                    keys.add(Wanderers.talkKey(wanderer, place, i));
                }
            }
            assertEquals(wanderer + ": two skills", 2, wanderer.skills().size());
            for (Wanderers.Skill skill : wanderer.skills()) {
                keys.add(skill.shoutKey());
            }
            for (String key : keys) {
                assertTrue("ja: " + key, ja.has(key) && !ja.get(key).getAsString().isBlank());
                assertTrue("en: " + key, en.has(key) && !en.get(key).getAsString().isBlank());
            }
        }
        assertTrue(ja.has("message.eternalrealm.wanderer.met"));
        assertTrue(ja.has("entity.eternalrealm.wanderer"));
    }

    @Test
    public void eachFightsInItsOwnWay() {
        Set<Wanderers.Style> styles = EnumSet.noneOf(Wanderers.Style.class);
        for (Wanderers.Wanderer wanderer : Wanderers.Wanderer.values()) {
            assertTrue(styles.add(wanderer.style()));
            assertTrue(wanderer.range() >= 2.0 && wanderer.range() <= 10.0);
            assertTrue(wanderer.basicDamage() > 0.0 && wanderer.basicDamage() < 0.15);
            assertTrue("at least one skill that fights",
                    wanderer.skills().stream().anyMatch(skill -> !skill.heals()));
            for (Wanderers.Skill skill : wanderer.skills()) {
                assertTrue(skill.cooldownTicks() >= 100);
                assertTrue("a skill takes a share, never a whole monster", skill.power() > 0.0 && skill.power() < 0.5);
            }
        }
        assertTrue(Wanderers.Skill.BUBBLE_WARD.heals());
        assertEquals(Wanderers.Wanderer.GRAUS, Wanderers.Wanderer.byId("graus").orElseThrow());
        assertTrue(Wanderers.Wanderer.byId("nobody").isEmpty());
    }

    @Test
    public void theRoundGoesFromTheFieldToTownAndBack() {
        for (double roll : new double[]{0.0, 0.3, 0.7, 0.99}) {
            assertNotEquals("never two field trips in a row", Wanderers.Place.FIELD,
                    Wanderers.nextPlace(Wanderers.Place.FIELD, roll));
            assertEquals("out again after the inn", Wanderers.Place.FIELD,
                    Wanderers.nextPlace(Wanderers.Place.INN, roll));
        }
        assertEquals(Wanderers.Place.INN, Wanderers.nextPlace(Wanderers.Place.SHOP, 0.2));
        assertEquals(Wanderers.Place.FIELD, Wanderers.nextPlace(Wanderers.Place.SHOP, 0.8));
        assertEquals(20 * 60 * 6, Wanderers.stay(Wanderers.Place.FIELD, false, 0.0));
        assertEquals(20 * 60 * 10, Wanderers.stay(Wanderers.Place.FIELD, false, 1.0));
        assertEquals(20 * 60 * 2, Wanderers.stay(Wanderers.Place.SHOP, true, 0.0));
        assertTrue("longer at the inn at night",
                Wanderers.stay(Wanderers.Place.INN, true, 0.5) > Wanderers.stay(Wanderers.Place.INN, false, 0.5));
    }

    @Test
    public void mostFieldTripsGoNearThePlayerButNeverBackToTheSameHabitat() {
        List<Habitats.Habitat> all = Habitats.all();
        Habitats.Habitat here = all.get(0);
        Random random = new Random(7);
        int near = 0;
        for (int i = 0; i < 400; i++) {
            Habitats.Habitat picked = Wanderers.pickHabitat(all, here.id(), here.x(), here.z(), random).orElseThrow();
            assertNotEquals("never the one it just left", here.id(), picked.id());
            if (picked.distanceTo(here.x(), here.z()) <= Wanderers.NEAR_PLAYER) {
                near++;
            }
        }
        assertTrue("most trips near the player: " + near, near > 200);
        assertTrue("but not all: " + near, near < 400);
        assertTrue(Wanderers.pickHabitat(List.of(), "", 0.0, 0.0, random).isEmpty());
        assertTrue("nothing but the one it left", Wanderers.pickHabitat(List.of(here), here.id(), 0.0, 0.0, random)
                .isEmpty());
    }

    @Test
    public void plansAndMeetings() {
        assertThrows(IllegalArgumentException.class, () -> new Wanderers.Plan(Wanderers.Place.FIELD, "", 0L));
        assertThrows(IllegalArgumentException.class, () -> new Wanderers.Plan(Wanderers.Place.INN, "f1_x", 0L));
        assertEquals(Wanderers.Place.SHOP, new Wanderers.Plan(Wanderers.Place.SHOP, "", 5L).place());
        Set<Wanderers.Wanderer> met = Wanderers.parseMet("sele, bold,nobody");
        assertEquals(EnumSet.of(Wanderers.Wanderer.SELE, Wanderers.Wanderer.BOLD), met);
        assertEquals(met, Wanderers.parseMet(Wanderers.formatMet(met)));
        assertTrue(Wanderers.parseMet("").isEmpty());
        assertTrue(Wanderers.parseMet(null).isEmpty());
        assertFalse(Wanderers.formatMet(met).contains(" "));
        assertEquals(Wanderers.talkKey(Wanderers.Wanderer.BOLD, Wanderers.Place.INN, 0),
                Wanderers.talkKey(Wanderers.Wanderer.BOLD, Wanderers.Place.INN, Wanderers.LINES_PER_PLACE));
        assertEquals("wanderer.eternalrealm.mirka.field.2",
                Wanderers.talkKey(Wanderers.Wanderer.MIRKA, Wanderers.Place.FIELD, 1));
    }
}
